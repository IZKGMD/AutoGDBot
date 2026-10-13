#include <cstddef>
#include <cstdint>

// Geode's Android runtime currently exposes an older libc++ than the newest
// NDK headers used by some CI builds. New libc++ may lower pointer hashing to
// this private helper. Export a DSO-local compatibility implementation so the
// mod never depends on that private runtime symbol.
//
// The hash only needs to be deterministic for the lifetime of the process.
// FNV-1a is sufficient for the unordered containers that reference this helper.
extern "C" __attribute__((visibility("default"), used))
std::size_t agb_ndk_hash_memory(void const *data, std::size_t size)
    asm("_ZNSt6__ndk113__hash_memoryEPKvm");

extern "C" __attribute__((visibility("default"), used))
std::size_t agb_ndk_hash_memory(void const *data, std::size_t size) {
    auto const *bytes = static_cast<unsigned char const *>(data);
#if INTPTR_MAX == INT64_MAX
    std::size_t hash = static_cast<std::size_t>(1469598103934665603ull);
    constexpr std::size_t prime = static_cast<std::size_t>(1099511628211ull);
#else
    std::size_t hash = static_cast<std::size_t>(2166136261u);
    constexpr std::size_t prime = static_cast<std::size_t>(16777619u);
#endif
    for (std::size_t i = 0; i < size; ++i) {
        hash ^= static_cast<std::size_t>(bytes[i]);
        hash *= prime;
    }
    return hash;
}

