cmake_minimum_required(VERSION 3.25)

project(DroidFS)

option(GOCRYPTFS "build libgocryptfs" ON)
option(CRYFS "build libcryfs" ON)

add_library(memfile SHARED src/main/native/memfile.c)
target_link_libraries(memfile log)

if (GOCRYPTFS)
	add_library(gocryptfs SHARED IMPORTED)
	set_target_properties(
		gocryptfs
		PROPERTIES IMPORTED_LOCATION
		${PROJECT_SOURCE_DIR}/libgocryptfs/build/${ANDROID_ABI}/libgocryptfs.so
	)
	add_library(gocryptfs_jni SHARED src/main/native/gocryptfs_jni.c)
	target_include_directories(gocryptfs_jni PRIVATE ${PROJECT_SOURCE_DIR}/libgocryptfs/build/${ANDROID_ABI})
	target_link_libraries(gocryptfs_jni gocryptfs)
endif()

if (CRYFS)
	add_subdirectory(${PROJECT_SOURCE_DIR}/libcryfs)
	add_library(cryfs_jni SHARED src/main/native/libcryfs.c)
	target_link_libraries(cryfs_jni libcryfs-jni)
endif()

add_library(
        avformat
        SHARED
        IMPORTED
)

set_target_properties(
        avformat
        PROPERTIES IMPORTED_LOCATION
        ${PROJECT_SOURCE_DIR}/ffmpeg/build/${ANDROID_ABI}/libavformat/libavformat.so
)

add_library(
        avcodec
        SHARED
        IMPORTED
)

set_target_properties(
        avcodec
        PROPERTIES IMPORTED_LOCATION
        ${PROJECT_SOURCE_DIR}/ffmpeg/build/${ANDROID_ABI}/libavcodec/libavcodec.so
)

add_library(
        avutil
        SHARED
        IMPORTED
)

set_target_properties(
        avutil
        PROPERTIES IMPORTED_LOCATION
        ${PROJECT_SOURCE_DIR}/ffmpeg/build/${ANDROID_ABI}/libavutil/libavutil.so
)

add_library(
        mux
        SHARED
        src/main/native/libmux.c
)

target_include_directories(mux PRIVATE ${PROJECT_SOURCE_DIR}/ffmpeg/build/${ANDROID_ABI})

target_link_libraries(
        mux
        avformat
        avcodec
        avutil
        log
)

# RIFE 4.6 live frame interpolation. ncnn is built with its Vulkan backend and
# only the operators used by RIFE, keeping the APK considerably smaller than a
# general-purpose inference runtime.
set(RIFE_SOURCE_DIR ${PROJECT_SOURCE_DIR}/rife-ncnn-vulkan/src)

set(NCNN_INSTALL_SDK OFF CACHE BOOL "" FORCE)
set(NCNN_PIXEL_ROTATE OFF CACHE BOOL "" FORCE)
set(NCNN_PIXEL_AFFINE OFF CACHE BOOL "" FORCE)
set(NCNN_PIXEL_DRAWING OFF CACHE BOOL "" FORCE)
set(NCNN_VULKAN ON CACHE BOOL "" FORCE)
set(NCNN_VULKAN_ONLINE_SPIRV ON CACHE BOOL "" FORCE)
set(NCNN_BUILD_BENCHMARK OFF CACHE BOOL "" FORCE)
set(NCNN_BUILD_TESTS OFF CACHE BOOL "" FORCE)
set(NCNN_BUILD_TOOLS OFF CACHE BOOL "" FORCE)
set(NCNN_BUILD_EXAMPLES OFF CACHE BOOL "" FORCE)
set(NCNN_DISABLE_RTTI ON CACHE BOOL "" FORCE)
set(NCNN_DISABLE_EXCEPTION ON CACHE BOOL "" FORCE)
set(NCNN_INT8 OFF CACHE BOOL "" FORCE)

set(WITH_LAYER_absval OFF CACHE BOOL "" FORCE)
set(WITH_LAYER_argmax OFF CACHE BOOL "" FORCE)
set(WITH_LAYER_batchnorm OFF CACHE BOOL "" FORCE)
set(WITH_LAYER_bias OFF CACHE BOOL "" FORCE)
set(WITH_LAYER_bnll OFF CACHE BOOL "" FORCE)
set(WITH_LAYER_concat ON CACHE BOOL "" FORCE)
set(WITH_LAYER_convolution ON CACHE BOOL "" FORCE)
set(WITH_LAYER_crop ON CACHE BOOL "" FORCE)
set(WITH_LAYER_deconvolution ON CACHE BOOL "" FORCE)
set(WITH_LAYER_dropout OFF CACHE BOOL "" FORCE)
set(WITH_LAYER_eltwise ON CACHE BOOL "" FORCE)
set(WITH_LAYER_elu OFF CACHE BOOL "" FORCE)
set(WITH_LAYER_embed OFF CACHE BOOL "" FORCE)
set(WITH_LAYER_exp OFF CACHE BOOL "" FORCE)
set(WITH_LAYER_flatten ON CACHE BOOL "" FORCE)
set(WITH_LAYER_innerproduct ON CACHE BOOL "" FORCE)
set(WITH_LAYER_input ON CACHE BOOL "" FORCE)
set(WITH_LAYER_log OFF CACHE BOOL "" FORCE)
set(WITH_LAYER_lrn OFF CACHE BOOL "" FORCE)
set(WITH_LAYER_memorydata OFF CACHE BOOL "" FORCE)
set(WITH_LAYER_mvn OFF CACHE BOOL "" FORCE)
set(WITH_LAYER_pooling ON CACHE BOOL "" FORCE)
set(WITH_LAYER_power OFF CACHE BOOL "" FORCE)
set(WITH_LAYER_prelu ON CACHE BOOL "" FORCE)
set(WITH_LAYER_proposal OFF CACHE BOOL "" FORCE)
set(WITH_LAYER_reduction OFF CACHE BOOL "" FORCE)
set(WITH_LAYER_relu ON CACHE BOOL "" FORCE)
set(WITH_LAYER_reshape OFF CACHE BOOL "" FORCE)
set(WITH_LAYER_roipooling OFF CACHE BOOL "" FORCE)
set(WITH_LAYER_scale OFF CACHE BOOL "" FORCE)
set(WITH_LAYER_sigmoid ON CACHE BOOL "" FORCE)
set(WITH_LAYER_slice ON CACHE BOOL "" FORCE)
set(WITH_LAYER_softmax OFF CACHE BOOL "" FORCE)
set(WITH_LAYER_split ON CACHE BOOL "" FORCE)
set(WITH_LAYER_spp OFF CACHE BOOL "" FORCE)
set(WITH_LAYER_tanh OFF CACHE BOOL "" FORCE)
set(WITH_LAYER_threshold OFF CACHE BOOL "" FORCE)
set(WITH_LAYER_tile OFF CACHE BOOL "" FORCE)
set(WITH_LAYER_rnn OFF CACHE BOOL "" FORCE)
set(WITH_LAYER_lstm OFF CACHE BOOL "" FORCE)
set(WITH_LAYER_binaryop ON CACHE BOOL "" FORCE)
set(WITH_LAYER_unaryop ON CACHE BOOL "" FORCE)
set(WITH_LAYER_convolutiondepthwise OFF CACHE BOOL "" FORCE)
set(WITH_LAYER_padding ON CACHE BOOL "" FORCE)
set(WITH_LAYER_squeeze OFF CACHE BOOL "" FORCE)
set(WITH_LAYER_expanddims OFF CACHE BOOL "" FORCE)
set(WITH_LAYER_normalize OFF CACHE BOOL "" FORCE)
set(WITH_LAYER_permute OFF CACHE BOOL "" FORCE)
set(WITH_LAYER_priorbox OFF CACHE BOOL "" FORCE)
set(WITH_LAYER_detectionoutput OFF CACHE BOOL "" FORCE)
set(WITH_LAYER_interp ON CACHE BOOL "" FORCE)
set(WITH_LAYER_deconvolutiondepthwise OFF CACHE BOOL "" FORCE)
set(WITH_LAYER_shufflechannel OFF CACHE BOOL "" FORCE)
set(WITH_LAYER_instancenorm OFF CACHE BOOL "" FORCE)
set(WITH_LAYER_clip ON CACHE BOOL "" FORCE)
set(WITH_LAYER_reorg OFF CACHE BOOL "" FORCE)
set(WITH_LAYER_packing ON CACHE BOOL "" FORCE)
set(WITH_LAYER_cast ON CACHE BOOL "" FORCE)
set(WITH_LAYER_pixelshuffle ON CACHE BOOL "" FORCE)
set(WITH_LAYER_requantize OFF CACHE BOOL "" FORCE)
set(WITH_LAYER_quantize OFF CACHE BOOL "" FORCE)
set(WITH_LAYER_dequantize OFF CACHE BOOL "" FORCE)
set(WITH_LAYER_deepcopy OFF CACHE BOOL "" FORCE)
set(WITH_LAYER_hardsigmoid OFF CACHE BOOL "" FORCE)
set(WITH_LAYER_hardswish OFF CACHE BOOL "" FORCE)
set(WITH_LAYER_selu OFF CACHE BOOL "" FORCE)
set(WITH_LAYER_mish OFF CACHE BOOL "" FORCE)
set(WITH_LAYER_swish OFF CACHE BOOL "" FORCE)
set(WITH_LAYER_gemm OFF CACHE BOOL "" FORCE)
set(WITH_LAYER_groupnorm OFF CACHE BOOL "" FORCE)
set(WITH_LAYER_layernorm OFF CACHE BOOL "" FORCE)
set(WITH_LAYER_gru OFF CACHE BOOL "" FORCE)
set(WITH_LAYER_multiheadattention OFF CACHE BOOL "" FORCE)
set(WITH_LAYER_gelu OFF CACHE BOOL "" FORCE)
set(WITH_LAYER_matmul OFF CACHE BOOL "" FORCE)
set(WITH_LAYER_einsum OFF CACHE BOOL "" FORCE)
set(WITH_LAYER_deformableconv2d OFF CACHE BOOL "" FORCE)
set(WITH_LAYER_noop OFF CACHE BOOL "" FORCE)
set(WITH_LAYER_statisticspooling OFF CACHE BOOL "" FORCE)
set(WITH_LAYER_softplus OFF CACHE BOOL "" FORCE)
set(WITH_LAYER_convolution1d OFF CACHE BOOL "" FORCE)
set(WITH_LAYER_pooling1d OFF CACHE BOOL "" FORCE)
set(WITH_LAYER_convolutiondepthwise1d OFF CACHE BOOL "" FORCE)
set(WITH_LAYER_deconvolution1d OFF CACHE BOOL "" FORCE)
set(WITH_LAYER_deconvolutiondepthwise1d OFF CACHE BOOL "" FORCE)
set(WITH_LAYER_convolution3d OFF CACHE BOOL "" FORCE)
set(WITH_LAYER_convolutiondepthwise3d OFF CACHE BOOL "" FORCE)
set(WITH_LAYER_pooling3d OFF CACHE BOOL "" FORCE)
set(WITH_LAYER_deconvolution3d OFF CACHE BOOL "" FORCE)
set(WITH_LAYER_deconvolutiondepthwise3d OFF CACHE BOOL "" FORCE)

# Vulkan first appeared in Android 7. The application still supports older
# Android releases, so CMake's API-21 sysroot cannot find a linker stub. Link
# this optional library against the API-24 stub; the Kotlin side never loads it
# on older systems.
if(ANDROID_ABI STREQUAL "arm64-v8a")
    set(VULKAN_TRIPLE aarch64-linux-android)
elseif(ANDROID_ABI STREQUAL "armeabi-v7a")
    set(VULKAN_TRIPLE arm-linux-androideabi)
elseif(ANDROID_ABI STREQUAL "x86")
    set(VULKAN_TRIPLE i686-linux-android)
elseif(ANDROID_ABI STREQUAL "x86_64")
    set(VULKAN_TRIPLE x86_64-linux-android)
endif()
set(
    Vulkan_LIBRARY
    ${ANDROID_NDK}/toolchains/llvm/prebuilt/${ANDROID_HOST_TAG}/sysroot/usr/lib/${VULKAN_TRIPLE}/24/libvulkan.so
    CACHE FILEPATH "Android Vulkan loader stub" FORCE
)

add_subdirectory(${RIFE_SOURCE_DIR}/ncnn ${CMAKE_CURRENT_BINARY_DIR}/ncnn)

function(add_rife_shader shader_name)
    get_filename_component(shader_base ${shader_name} NAME_WE)
    set(shader_header ${CMAKE_CURRENT_BINARY_DIR}/rife-shaders/${shader_base}.comp.hex.h)
    add_custom_command(
        OUTPUT ${shader_header}
        COMMAND ${CMAKE_COMMAND} -E make_directory ${CMAKE_CURRENT_BINARY_DIR}/rife-shaders
        COMMAND ${CMAKE_COMMAND}
            -DSHADER_SRC=${RIFE_SOURCE_DIR}/${shader_name}
            -DSHADER_COMP_HEADER=${shader_header}
            -P ${RIFE_SOURCE_DIR}/generate_shader_comp_header.cmake
        DEPENDS ${RIFE_SOURCE_DIR}/${shader_name}
        VERBATIM
    )
    set(RIFE_SHADER_HEADERS ${RIFE_SHADER_HEADERS} ${shader_header} PARENT_SCOPE)
endfunction()

foreach(rife_shader
        rife_preproc.comp
        rife_postproc.comp
        rife_preproc_tta.comp
        rife_postproc_tta.comp
        rife_flow_tta_avg.comp
        rife_v2_flow_tta_avg.comp
        rife_v4_flow_tta_avg.comp
        rife_flow_tta_temporal_avg.comp
        rife_v2_flow_tta_temporal_avg.comp
        rife_v4_flow_tta_temporal_avg.comp
        rife_out_tta_temporal_avg.comp
        rife_v4_timestep.comp
        rife_v4_timestep_tta.comp
        warp.comp
        warp_pack4.comp
        warp_pack8.comp)
    add_rife_shader(${rife_shader})
endforeach()

add_custom_target(rife_shaders DEPENDS ${RIFE_SHADER_HEADERS})

add_library(
        live_slo_mo
        SHARED
        src/main/native/live_slo_mo.cpp
        ${RIFE_SOURCE_DIR}/rife.cpp
        ${RIFE_SOURCE_DIR}/warp.cpp
)
add_dependencies(live_slo_mo rife_shaders)
target_include_directories(
        live_slo_mo
        PRIVATE
        ${RIFE_SOURCE_DIR}
        ${CMAKE_CURRENT_BINARY_DIR}/rife-shaders
)
target_compile_features(live_slo_mo PRIVATE cxx_std_17)
target_compile_options(live_slo_mo PRIVATE -O3)
target_link_libraries(live_slo_mo ncnn android jnigraphics log)
