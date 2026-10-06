#include "gpu_ranker.hpp"
#include <d3d11.h>
#include <d3dcompiler.h>
#include <wrl/client.h>
using Microsoft::WRL::ComPtr;
namespace agb {
// Approximate GPU model: ordering only, never a collision verdict.
static constexpr char Shader[] = R"hlsl(
cbuffer State:register(b0){float4 pose;float4 params;uint4 cfg;uint4 counts;};
StructuredBuffer<uint> inputs:register(t0);
StructuredBuffer<float4> boxes:register(t1);
RWStructuredBuffer<float> results:register(u0);
[numthreads(64,1,1)] void main(uint3 tid:SV_DispatchThreadID){
 uint id=tid.x;if(id>=cfg.w)return;
 float2 p=pose.xy;float vy=pose.w,margin=100;uint survived=0;bool held=cfg.y!=0;
 for(uint t=0;t<cfg.z;++t){
  bool down=inputs[id*240+t]!=0;
  if(cfg.x==4)vy=(down?1:-1)*params.x*abs(pose.z)*4;
  else if(cfg.x==1||cfg.x==7)vy=clamp(vy+(down?1:-1)*params.x*params.z,-params.w,params.w);
  else {if(down&&!held&&(cfg.x==0||cfg.x==3||cfg.x==5))vy=params.x*10;
   vy=clamp(vy-params.x*params.z,-params.w,params.w);}
  held=down;p.x+=pose.z;p.y+=vy*.25;bool hit=false;
  for(uint j=0;j<counts.x;++j){float4 b=boxes[j];float2 d=max(max(b.xy-p,p-b.zw),0);
   float dist=length(d)-params.y;margin=min(margin,max(dist,0));if(dist<=0){hit=true;break;}}
  if(hit)break;survived++;
 }
 results[id]=survived*10000+margin;
})hlsl";
struct GpuRanker::Impl {
    ComPtr<ID3D11Device> d;
    ComPtr<ID3D11DeviceContext> c;
    ComPtr<ID3D11ComputeShader> shader;
    ComPtr<ID3D11Buffer> cb, input, boxes, out;
    ComPtr<ID3D11ShaderResourceView> iv, bv;
    ComPtr<ID3D11UnorderedAccessView> ov;
    struct Slot {
        ComPtr<ID3D11Buffer> b;
        ComPtr<ID3D11Query> q;
        bool pending = false;
        size_t count = 0;
        uint64_t serial = 0;
    } slots[2];
    bool valid = false;
    uint64_t serial = 0;
    std::string message = "GPU off";
    template <class T>
    bool buffer(ComPtr<ID3D11Buffer> &b, UINT n, UINT bind, D3D11_USAGE use, UINT access,
                bool structured) {
        D3D11_BUFFER_DESC x{};
        x.ByteWidth = sizeof(T) * n;
        x.Usage = use;
        x.BindFlags = bind;
        x.CPUAccessFlags = access;
        if (structured) {
            x.MiscFlags = D3D11_RESOURCE_MISC_BUFFER_STRUCTURED;
            x.StructureByteStride = sizeof(T);
        }
        return SUCCEEDED(d->CreateBuffer(&x, nullptr, &b));
    }
};
GpuRanker::GpuRanker() : impl_(std::make_unique<Impl>()) {}
GpuRanker::~GpuRanker() = default;
bool GpuRanker::ready() const {
    return impl_->valid;
}
std::string GpuRanker::status() const {
    return impl_->message;
}
bool GpuRanker::init() {
    auto &g = *impl_;
    if (g.valid)
        return true;
    D3D_FEATURE_LEVEL level{}, wanted[] = {D3D_FEATURE_LEVEL_11_0};
    auto hr = D3D11CreateDevice(nullptr, D3D_DRIVER_TYPE_HARDWARE, nullptr, 0, wanted, 1,
                                D3D11_SDK_VERSION, &g.d, &level, &g.c);
    if (FAILED(hr)) {
        g.message = "GPU unavailable: CPU fallback";
        return false;
    }
    ComPtr<ID3DBlob> code, errors;
    hr = D3DCompile(Shader, sizeof(Shader) - 1, "AutoGDBot", nullptr, nullptr, "main", "cs_5_0",
                    D3DCOMPILE_OPTIMIZATION_LEVEL3, 0, &code, &errors);
    if (FAILED(hr) || FAILED(g.d->CreateComputeShader(code->GetBufferPointer(),
                                                      code->GetBufferSize(), nullptr, &g.shader))) {
        g.message = "GPU shader failed";
        return false;
    }
    bool ok = g.buffer<uint32_t>(g.input, MaxCandidates * MaxHorizon, D3D11_BIND_SHADER_RESOURCE,
                                 D3D11_USAGE_DEFAULT, 0, true) &&
              g.buffer<Obstacle>(g.boxes, 256, D3D11_BIND_SHADER_RESOURCE, D3D11_USAGE_DEFAULT, 0,
                                 true) &&
              g.buffer<float>(g.out, MaxCandidates, D3D11_BIND_UNORDERED_ACCESS,
                              D3D11_USAGE_DEFAULT, 0, true) &&
              g.buffer<float>(g.cb, 16, D3D11_BIND_CONSTANT_BUFFER, D3D11_USAGE_DEFAULT, 0, false);
    D3D11_SHADER_RESOURCE_VIEW_DESC s{};
    s.ViewDimension = D3D11_SRV_DIMENSION_BUFFER;
    s.Format = DXGI_FORMAT_UNKNOWN;
    s.Buffer.NumElements = MaxCandidates * MaxHorizon;
    ok = ok && SUCCEEDED(g.d->CreateShaderResourceView(g.input.Get(), &s, &g.iv));
    s.Buffer.NumElements = 256;
    ok = ok && SUCCEEDED(g.d->CreateShaderResourceView(g.boxes.Get(), &s, &g.bv));
    D3D11_UNORDERED_ACCESS_VIEW_DESC u{};
    u.ViewDimension = D3D11_UAV_DIMENSION_BUFFER;
    u.Format = DXGI_FORMAT_UNKNOWN;
    u.Buffer.NumElements = MaxCandidates;
    ok = ok && SUCCEEDED(g.d->CreateUnorderedAccessView(g.out.Get(), &u, &g.ov));
    D3D11_QUERY_DESC q{};
    q.Query = D3D11_QUERY_EVENT;
    for (auto &slot : g.slots)
        ok = ok &&
             g.buffer<float>(slot.b, MaxCandidates, 0, D3D11_USAGE_STAGING, D3D11_CPU_ACCESS_READ,
                             false) &&
             SUCCEEDED(g.d->CreateQuery(&q, &slot.q));
    g.valid = ok;
    g.message = ok ? "D3D11 async" : "GPU buffers failed";
    return ok;
}
std::vector<float> GpuRanker::poll() {
    auto &g = *impl_;
    std::vector<float> result;
    uint64_t newest = 0;
    if (!g.valid)
        return result;
    for (auto &s : g.slots) {
        if (!s.pending)
            continue;
        BOOL finished = FALSE;
        if (g.c->GetData(s.q.Get(), &finished, sizeof(finished), D3D11_ASYNC_GETDATA_DONOTFLUSH) !=
                S_OK ||
            !finished)
            continue;
        D3D11_MAPPED_SUBRESOURCE m{};
        auto hr = g.c->Map(s.b.Get(), 0, D3D11_MAP_READ, D3D11_MAP_FLAG_DO_NOT_WAIT, &m);
        if (hr == DXGI_ERROR_WAS_STILL_DRAWING)
            continue;
        if (FAILED(hr)) {
            g.valid = false;
            g.message = "GPU readback failed";
            break;
        }
        if (s.serial >= newest) {
            auto *v = static_cast<float const *>(m.pData);
            result.assign(v, v + s.count);
            newest = s.serial;
            for (auto &x : result)
                if (!std::isfinite(x))
                    x = -1;
        }
        g.c->Unmap(s.b.Get(), 0);
        s.pending = false;
    }
    return result;
}
bool GpuRanker::submit(GpuState state, std::vector<Plan> const &plans,
                       std::vector<Obstacle> const &hazards) {
    auto &g = *impl_;
    if (!g.valid || plans.empty())
        return false;
    Impl::Slot *slot = nullptr;
    for (auto &s : g.slots)
        if (!s.pending) {
            slot = &s;
            break;
        }
    if (!slot)
        return false;
    size_t count = std::min(plans.size(), size_t(MaxCandidates));
    std::array<uint32_t, MaxCandidates * MaxHorizon> input{};
    std::array<Obstacle, 256> boxes{};
    for (size_t i = 0; i < count; ++i)
        for (int t = 0; t < MaxHorizon; ++t)
            input[i * MaxHorizon + t] = plans[i].at(t);
    size_t n = std::min(hazards.size(), boxes.size());
    std::copy_n(hazards.begin(), n, boxes.begin());
    struct alignas(16) Constants {
        GpuState state;
        uint32_t hazards;
        uint32_t pad[3]{};
    } k{};
    static_assert(sizeof(Constants) == 64);
    k.state = state;
    k.state.count = uint32_t(count);
    k.hazards = uint32_t(n);
    g.c->UpdateSubresource(g.cb.Get(), 0, nullptr, &k, 0, 0);
    g.c->UpdateSubresource(g.input.Get(), 0, nullptr, input.data(), 0, 0);
    g.c->UpdateSubresource(g.boxes.Get(), 0, nullptr, boxes.data(), 0, 0);
    auto *cb = g.cb.Get();
    auto *uav = g.ov.Get();
    ID3D11ShaderResourceView *srvs[] = {g.iv.Get(), g.bv.Get()};
    g.c->CSSetShader(g.shader.Get(), nullptr, 0);
    g.c->CSSetConstantBuffers(0, 1, &cb);
    g.c->CSSetShaderResources(0, 2, srvs);
    g.c->CSSetUnorderedAccessViews(0, 1, &uav, nullptr);
    g.c->Dispatch(UINT((count + 63) / 64), 1, 1);
    ID3D11UnorderedAccessView *nu = nullptr;
    ID3D11ShaderResourceView *ns[] = {nullptr, nullptr};
    g.c->CSSetUnorderedAccessViews(0, 1, &nu, nullptr);
    g.c->CSSetShaderResources(0, 2, ns);
    g.c->CopyResource(slot->b.Get(), g.out.Get());
    g.c->End(slot->q.Get());
    slot->pending = true;
    slot->count = count;
    slot->serial = ++g.serial;
    g.c->Flush();
    return true;
}
} // namespace agb
