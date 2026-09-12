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
    if (argc != 4) { fprintf(stderr, "touch_probe /dev/input/eventN duration_ms count\n"); return 2; }
    int duration = atoi(argv[2]), count = atoi(argv[3]);
    if (duration < 8 || duration > 200 || count < 4 || count > 256) return 2;
    fd = open(argv[1], O_WRONLY | O_CLOEXEC); if (fd < 0) { perror("open"); return 2; }
    char name[256] = {0}; struct input_absinfo ax, ay;
    if (ioctl(fd, EVIOCGNAME(sizeof(name)), name) < 0 || strcmp(name, "NVTCapacitiveTouchScreen") ||
        ioctl(fd, EVIOCGABS(ABS_MT_POSITION_X), &ax) < 0 || ioctl(fd, EVIOCGABS(ABS_MT_POSITION_Y), &ay) < 0) {
        fprintf(stderr, "Only the verified Motorola NVT touchscreen is accepted\n"); close(fd); fd = -1; return 2;
    }
    signal(SIGINT, interrupted); signal(SIGTERM, interrupted); signal(SIGPIPE, interrupted); atexit(release);
    setvbuf(stdout, NULL, _IOLBF, 0);
    int x = (ax.minimum + ax.maximum) / 2, y = ay.minimum + (ay.maximum - ay.minimum) * 58 / 100;
    int travel = (ax.maximum - ax.minimum) * 20 / 100;
    long long start = ns() + 500000000LL;
    printf("trial,action,phase,step,ns,x,y\n");
    for (int i = 0; i < count; i++) {
        int dx = i % 4 == 0 ? -travel : i % 4 == 1 ? travel : 0;
        int dy = i % 4 == 2 ? -travel : i % 4 == 3 ? travel : 0;
        // Sweep through display phases instead of locking to a multiple of 60 Hz.
        long long at = start + i * 1007000000LL;
        until(at);
        long long stamp = ns();
        event(EV_ABS, ABS_MT_SLOT, 0); event(EV_ABS, ABS_MT_TRACKING_ID, i + 1);
        event(EV_ABS, ABS_MT_POSITION_X, x); event(EV_ABS, ABS_MT_POSITION_Y, y);
        event(EV_ABS, ABS_MT_PRESSURE, 50); event(EV_ABS, ABS_MT_TOUCH_MAJOR, 8);
        event(EV_KEY, BTN_TOUCH, 1); event(EV_SYN, SYN_REPORT, 0);
        printf("%d,%d,down,0,%lld,%d,%d\n", i, i % 4 + 1, stamp, x, y);
        int steps = duration / 8;
        for (int j = 1; j <= steps; j++) {
            until(at + (long long)duration * 1000000LL * j / steps);
            int xx = x + dx * j / steps, yy = y + dy * j / steps;
            stamp = ns();
            event(EV_ABS, ABS_MT_POSITION_X, xx); event(EV_ABS, ABS_MT_POSITION_Y, yy); event(EV_SYN, SYN_REPORT, 0);
            printf("%d,%d,move,%d,%lld,%d,%d\n", i, i % 4 + 1, j, stamp, xx, yy);
        }
        until(at + ((long long)duration + 8) * 1000000LL);
        stamp = ns();
        event(EV_ABS, ABS_MT_TRACKING_ID, -1); event(EV_KEY, BTN_TOUCH, 0); event(EV_SYN, SYN_REPORT, 0);
        printf("%d,%d,up,0,%lld,%d,%d\n", i, i % 4 + 1, stamp, x + dx, y + dy);
    }
    return 0;
}
