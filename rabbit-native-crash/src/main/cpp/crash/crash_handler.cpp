//
// Created by susion wang on 2020-03-25.
//

#include "crash_handler.h"
#include <csignal>
#include "spi.h"
#include "malloc.h"
#include <sys/syscall.h>
#include <cstring>
#include <unistd.h>
#include <pthread.h>

#define DUMP_FUNCTION_STACK_SIZE  (1024*128)

typedef struct {
    int signum;
    struct sigaction oldact;
} CrashSignalInfo;

//支持的 native crash 捕获类型
static CrashSignalInfo support_crash_infos[] =
        {
                {.signum = SIGABRT},
                {.signum = SIGBUS},
                {.signum = SIGFPE},
                {.signum = SIGILL},
                {.signum = SIGSEGV},
                {.signum = SIGTRAP},
                {.signum = SIGSYS},
                {.signum = SIGSTKFLT}
        };

//native code crash 回调
sig_atomic_t has_capture_crash = 0;

static void crash_handler(int sig, siginfo_t *si, void *uc) {

    if (has_capture_crash) {
        LOG_D("已经捕获到 native crash!");
        return;
    }

    has_capture_crash = 1;
    LOG_D("捕获到native crash!");

    notify_crash_to_java();

    //退出程序
//    _exit(1);
}

//注册native crash 回调
int register_crash_signal_handler(JNIEnv *env, jobject javaApiObj) {

    //1. 分配处理 native crash 的函数堆栈 (紧急情况下使用)
    stack_t dump_stack;
    dump_stack.ss_sp = calloc(1, DUMP_FUNCTION_STACK_SIZE);
    if (dump_stack.ss_sp == nullptr) {
        LOG_D("malloc dump function stack failed!");
        return ERROR_CODE_INT;
    }
    dump_stack.ss_size = DUMP_FUNCTION_STACK_SIZE;
    dump_stack.ss_flags = 0;

    //2. 注册 紧急堆栈
    int res = sigaltstack(&dump_stack, nullptr);
    if (res != 0) {
        LOG_D("register dump function stack failed!");
        return ERROR_CODE_INT;
    }

    //3. 注册crash信号处理函数
    struct sigaction crash_act{};
    memset(&crash_act, 0, sizeof(crash_act));
    sigfillset(&crash_act.sa_mask);
    crash_act.sa_sigaction = crash_handler;
    crash_act.sa_flags = SA_RESTART | SA_SIGINFO | SA_ONSTACK;

    int sig_size = sizeof(support_crash_infos) / sizeof(support_crash_infos[0]);
    LOG_D("resister signal size : %d", sig_size);

    for (int i = 0; i < sig_size; i++) {

        CrashSignalInfo cur_info = support_crash_infos[i];

        res = sigaction(cur_info.signum, &crash_act, &(cur_info.oldact));

        if (res != 0) {
            LOG_D("resister sigaction : %d handler failed", cur_info.signum);
            return ERROR_CODE_INT;
        }
    }

    LOG_D("resister sigaction success!");

    return SUCCESS_CODE_INT;

}

