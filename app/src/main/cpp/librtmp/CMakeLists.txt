#关闭ssl 不支持rtmps  rtmp   加密  传递一变量  进制 加密验证
set(CMAKE_C_FLAGS "${CMAKE_C_FLAGS} -DNO_CRYPTO"  )

# 所有.c结尾的都进行编译
file(GLOB rtmp_source  *.c)

# 编译成静态库rtmp
add_library(rtmp STATIC ${rtmp_source})

