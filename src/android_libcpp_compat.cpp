#include <cstddef>
#include <cstdint>
#include <__functional/hash.h>

// Geode's Android runtime currently exposes an older libc++ than the newest
// NDK headers used by some CI builds. New libc++ may lower pointer hashing to
// this private helper. Export a DSO-local compatibility implementation so the
// mod never depends on that private runtime symbol.
//
// Match libc++'s actual Murmur2/CityHash implementation. An arbitrary hash
// such as FNV is not interchangeable for containers copied across the game /
// mod boundary: their stored hashes and later native lookups must agree.
extern "C" __attribute__((visibility("hidden"), used))
std::size_t agb_ndk_hash_memory(void const *data, std::size_t size)
    asm("_ZNSt6__ndk113__hash_memoryEPKvm");

extern "C" __attribute__((visibility("hidden"), used))
std::size_t agb_ndk_hash_memory(void const *data, std::size_t size) {
    return std::__murmur2_or_cityhash<std::size_t>{}(data, size);
}
