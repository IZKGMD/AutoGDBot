#include <Geode/Geode.hpp>
#include <Geode/modify/PlayLayer.hpp>

#ifdef GEODE_IS_WINDOWS
#include <winsock2.h>
#pragma comment(lib, "ws2_32.lib") // Авто-подключение сокетов без CMake!
#endif

using namespace geode::prelude;

SOCKET sock;
sockaddr_in server_addr;
bool socket_initialized = false;

struct Hitbox { float x, y, w, h; int isHazard; };

struct GameState {
    float pX, pY, pYAccel;
    int isGrounded;
    int gameMode;
    int isUpsideDown;
    int isGoingLeft;
    int objCount;
    Hitbox objects[20];
};

void initSocket() {
#ifdef GEODE_IS_WINDOWS
    if (socket_initialized) return;
    WSADATA wsa;
    WSAStartup(MAKEWORD(2, 2), &wsa);
    sock = socket(AF_INET, SOCK_DGRAM, IPPROTO_UDP);
    server_addr.sin_family = AF_INET;
    server_addr.sin_port = htons(9999);
    server_addr.sin_addr.s_addr = inet_addr("127.0.0.1");
    u_long mode = 1;
    ioctlsocket(sock, FIONBIO, &mode);
    socket_initialized = true;
#endif
}

class $modify(MyPlayLayer, PlayLayer) {
    bool init(GJGameLevel* level, bool useReplay, bool dontCreateObjects) {
        if (!PlayLayer::init(level, useReplay, dontCreateObjects)) return false;
        initSocket();
        return true;
    }

    void postUpdate(float p0) {
        PlayLayer::postUpdate(p0);
#ifdef GEODE_IS_WINDOWS
        if (!m_player1 || m_player1->m_isDead) return;

        GameState state = {0};
        state.pX = m_player1->m_position.x;
        state.pY = m_player1->m_position.y;
        state.pYAccel = m_player1->m_yAccel;
        state.isGrounded = m_player1->m_isOnGround ? 1 : 0;
        state.isUpsideDown = m_player1->m_isUpsideDown ? 1 : 0;
        state.isGoingLeft = (m_player1->getScaleX() < 0) ? 1 : 0;

        if (m_player1->m_isShip) state.gameMode = 1;
        else if (m_player1->m_isBall) state.gameMode = 2;
        else if (m_player1->m_isBird) state.gameMode = 3;
        else if (m_player1->m_isDart) state.gameMode = 4;
        else if (m_player1->m_isRobot) state.gameMode = 5;
        else if (m_player1->m_isSpider) state.gameMode = 6;
        else if (m_player1->m_isSwing) state.gameMode = 7;
        else state.gameMode = 0;

        if (m_objects) {
            for (int i = 0; i < m_objects->count(); i++) {
                auto obj = typeinfo_cast<GameObject*>(m_objects->objectAtIndex(i));
                if (!obj) continue;
                float ox = obj->m_position.x;
                bool inRange = state.isGoingLeft ? 
                               (ox < state.pX && ox > state.pX - 800.f) : 
                               (ox > state.pX && ox < state.pX + 800.f);

                if (inRange) {
                    CCRect rect = obj->getObjectRect();
                    state.objects[state.objCount++] = {
                        rect.origin.x, rect.origin.y, rect.size.width, rect.size.height,
                        (obj->m_objectType == GameObjectType::Hazard) ? 1 : 0
                    };
                    if (state.objCount >= 20) break;
                }
            }
        }

        sendto(sock, (const char*)&state, sizeof(GameState), 0, (sockaddr*)&server_addr, sizeof(server_addr));

        int action = 0;
        int slen = sizeof(server_addr);
        if (recvfrom(sock, (char*)&action, sizeof(int), 0, (sockaddr*)&server_addr, &slen) > 0) {
            if (action == 1) m_player1->pushButton(PlayerButton::Jump);
            else if (action == 2) m_player1->releaseButton(PlayerButton::Jump);
        }
#endif
    }
};