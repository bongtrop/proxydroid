add_executable(redsocks
        src/base.c src/dnstc.c src/http-connect.c
        src/log.c src/md5.c src/socks5.c
        src/base64.c src/http-auth.c src/http-relay.c src/main.c
        src/parser.c src/redsocks.c src/socks4.c src/utils.c
        )

target_include_directories(redsocks
        PUBLIC
#        ${CMAKE_SOURCE_DIR}/redsocks/src
        ${CMAKE_SOURCE_DIR}/libevent
        ${CMAKE_SOURCE_DIR}/libevent/include
        )

# Include libraries needed for exec lib
target_link_libraries(redsocks
        event
        android
        dl
        log)
