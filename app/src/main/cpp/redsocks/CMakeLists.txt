add_executable(redsocks
        redsocks/base.c redsocks/dnstc.c redsocks/http-connect.c
        redsocks/log.c redsocks/md5.c redsocks/socks5.c
        redsocks/base64.c redsocks/http-auth.c redsocks/http-relay.c redsocks/main.c
        redsocks/parser.c redsocks/redsocks.c redsocks/socks4.c redsocks/utils.c
        )

target_include_directories(redsocks
        PUBLIC
        ${CMAKE_SOURCE_DIR}/libevent
        ${CMAKE_SOURCE_DIR}/libevent/include
        )

# Include libraries needed for exec lib
target_link_libraries(redsocks
        event
        android
        dl
        log)
