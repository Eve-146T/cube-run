// Bounded touchscreen injection probe. Never creates a device or changes its settings.
#include <linux/input.h>
#include <sys/ioctl.h>
#include <fcntl.h>
#include <signal.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <time.h>
#include <unistd.h>
static int fd = -1;
static void event(int type, int code, int value) {
    struct input_event e = {0}; e.type = type; e.code = code; e.value = value;
    if (write(fd, &e, sizeof(e)) != sizeof(e)) { perror("input write"); exit(2); }
}
static void release(void) {
    if (fd >= 0) {
        // Cleanup must not call exit() recursively if the device disconnects.
        struct input_event events[4] = {0};
        events[0].type = EV_ABS; events[0].code = ABS_MT_SLOT;
        events[1].type = EV_ABS; events[1].code = ABS_MT_TRACKING_ID; events[1].value = -1;
        events[2].type = EV_KEY; events[2].code = BTN_TOUCH;
        events[3].type = EV_SYN; events[3].code = SYN_REPORT;
        (void)write(fd, events, sizeof(events)); close(fd); fd = -1;
    }
}
static void interrupted(int signal) { (void)signal; release(); _exit(130); }
static long long ns(void) { struct timespec t; clock_gettime(CLOCK_MONOTONIC, &t); return (long long)t.tv_sec * 1000000000LL + t.tv_nsec; }
static void until(long long n) { struct timespec t = {n / 1000000000LL, n % 1000000000LL}; clock_nanosleep(CLOCK_MONOTONIC, TIMER_ABSTIME, &t, 0); }
int main(int argc, char **argv) {
    if (argc != 4) { fprintf(stderr, "input_probe /dev/input/eventN gestures_per_s count\n"); return 2; }
    int rate = atoi(argv[2]), count = atoi(argv[3]);
    if (rate < 1 || rate > 480 || count < 1 || count > 960) return 2;
    fd = open(argv[1], O_WRONLY | O_CLOEXEC); if (fd < 0) { perror("open"); return 2; }
    char name[256] = {0}; struct input_absinfo ax, ay;
    if (ioctl(fd, EVIOCGNAME(sizeof(name)), name) < 0 || strcmp(name, "NVTCapacitiveTouchScreen") ||
        ioctl(fd, EVIOCGABS(ABS_MT_POSITION_X), &ax) < 0 || ioctl(fd, EVIOCGABS(ABS_MT_POSITION_Y), &ay) < 0) {
        fprintf(stderr, "This probe only accepts the verified Motorola NVT touchscreen\n"); close(fd); return 2;
    }
    signal(SIGINT, interrupted); signal(SIGTERM, interrupted); atexit(release);
    int x = (ax.minimum + ax.maximum) / 2, y = ay.minimum + (ay.maximum - ay.minimum) * 58 / 100;
    int travel = (ax.maximum - ax.minimum) * 13 / 100;
    long long period = 1000000000LL / rate, start = ns() + 200000000LL;
    printf("START %lld\n", start / 1000000); fflush(stdout);
    for (int i = 0; i < count; i++) {
        int dx = i % 4 == 0 ? -travel : i % 4 == 1 ? travel : 0;
        int dy = i % 4 == 2 ? -travel : i % 4 == 3 ? travel : 0;
        until(start + i * period);
        event(EV_ABS, ABS_MT_SLOT, 0); event(EV_ABS, ABS_MT_TRACKING_ID, i + 1);
        event(EV_ABS, ABS_MT_POSITION_X, x); event(EV_ABS, ABS_MT_POSITION_Y, y);
        event(EV_ABS, ABS_MT_PRESSURE, 50); event(EV_ABS, ABS_MT_TOUCH_MAJOR, 8);
        event(EV_KEY, BTN_TOUCH, 1); event(EV_SYN, SYN_REPORT, 0);
        until(start + i * period + period / 3);
        event(EV_ABS, ABS_MT_POSITION_X, x + dx); event(EV_ABS, ABS_MT_POSITION_Y, y + dy); event(EV_SYN, SYN_REPORT, 0);
        until(start + i * period + period * 2 / 3);
        event(EV_ABS, ABS_MT_TRACKING_ID, -1); event(EV_KEY, BTN_TOUCH, 0); event(EV_SYN, SYN_REPORT, 0);
    }
    printf("ELAPSED %.9f\n", (ns() - start) / 1e9); fflush(stdout); return 0;
}
