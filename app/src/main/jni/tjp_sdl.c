/*
 * Copyright (C) 2020 OBONO
 * https://obono.hateblo.jp/
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <unistd.h>
#include <stdbool.h>
#include <time.h>
#include <SDL2/SDL.h>
#if __APPLE__
#include <OpenGL/gl.h>
#else
#include <GL/gl.h>
#endif
#include <ssd1306_virt.h>

#include "tjp_avr.h"

#define SOUND_BUFFER_LIMIT (sizeof(sound_buffer) - SOUND_BUFFER_SIZE)

static int      parse_cmdline(int argc, char *argv[]);
static uint64_t get_nsec_time(void);

static int      sdl_setup(void);
static int      sdl_loop(void);
static void     sdl_render_frame(int *pixels);
static void     sdl_sound_callback(void *unused, uint8_t *stream, int len);
static void     sdl_teardown(void);

static SDL_Window       *sdl_window;
static SDL_GLContext    sdl_gl_context;

static char *hex_file_path;
static int  pixel_size = 2;
static int  key2btn[BTN_COUNT] = {
    SDLK_UP,
    SDLK_DOWN,
    SDLK_LEFT,
    SDLK_RIGHT,
    SDLK_SPACE,
};
static char sound_buffer[SOUND_BUFFER_SIZE * 2];
static int  sound_buffer_available = 0;

/*---------------------------------------------------------------------------*/

int main(int argc, char *argv[])
{
    int ret;
    ret = parse_cmdline(argc, argv);
    if (ret) {
        goto done;
    }
    printf("Keymap: ");
    for (int i = 0; i < BTN_COUNT; i++) {
        printf("%d%c", key2btn[i], (i < BTN_COUNT - 1) ? '.' : '\n');
    }

    ret = tjp_avr_setup(hex_file_path);
    if (!ret) {
        ret = sdl_setup();
        if (!ret) {
            uint64_t last_nsec_time = get_nsec_time();
            while (!ret) {
                int pixels[OLED_WIDTH_PX * OLED_HEIGHT_PX];
                ret = sdl_loop();
                tjp_avr_loop(pixels);
                sdl_render_frame(pixels);
                uint64_t current_nsec_time = get_nsec_time();
                uint64_t target_nsec_time = last_nsec_time + (FRAME_PERIOD_US * 1000UL);
                if (target_nsec_time > current_nsec_time) {
                    usleep((target_nsec_time - current_nsec_time) / 1000UL);
                }
                last_nsec_time = current_nsec_time;
            }
            sdl_teardown();
        }
        tjp_avr_teardown();
    }

done:
    if (ret) {
        perror("Exiting with error");
    }
    return ret;
}

/*---------------------------------------------------------------------------*/

static void parse_keymap(char *arg)
{
    int i = 0;
    for (char *s = strtok(arg, ", "); s && i < BTN_COUNT; s = strtok(NULL, ", ")) {
        key2btn[i++] = atoi(s);
    }
}

static int parse_cmdline(int argc, char *argv[])
{
    char *cmd = argv[0];
    int ch, ret = -1;

    while ((ch = getopt(argc, argv, "hrk:p:")) != -1) {
        switch (ch) {
            case 'p':
                pixel_size = atoi(optarg);
                break;
            case 'k':
                parse_keymap(optarg);
                break;
            case 'r':
                tjp_avr_set_refresh_timing(true);
                break;
            case 'h':
                ret = 0;
                goto usage;
            default:
                goto usage;
        }
    }
    argc -= optind;
    argv += optind;
    if (argc) {
        hex_file_path = argv[0];
    } else {
        goto usage;
    }
    return 0;

usage:
    fprintf(stderr, "%s [-p pixel size] [-k keymap] [-r] [-h] filename.hex\n", cmd);
    return ret;
}

static uint64_t get_nsec_time(void)
{
    struct timespec tp;
    clock_gettime(CLOCK_MONOTONIC_RAW, &tp);
    return tp.tv_sec * 1000000000UL + tp.tv_nsec;
}

/*---------------------------------------------------------------------------*/

static inline enum button_e key_to_button_e(int keysym)
{
    for (int i = 0; i < BTN_COUNT; i++) {
        if (keysym == key2btn[i]) {
            return i;
        }
    }
    return -1;
}

static inline enum button_e dpad_to_button_e(uint8_t dpad_btn)
{
    switch (dpad_btn) {
        case SDL_CONTROLLER_BUTTON_DPAD_UP:
            return BTN_UP;
        case SDL_CONTROLLER_BUTTON_DPAD_DOWN:
            return BTN_DOWN;
        case SDL_CONTROLLER_BUTTON_DPAD_LEFT:
            return BTN_LEFT;
        case SDL_CONTROLLER_BUTTON_DPAD_RIGHT:
            return BTN_RIGHT;
        case SDL_CONTROLLER_BUTTON_A:
        case SDL_CONTROLLER_BUTTON_X:
        case SDL_CONTROLLER_BUTTON_B:
        case SDL_CONTROLLER_BUTTON_Y:
            return BTN_A;
    }
    return -1;
}

static void key_event(int key, bool pressed)
{
    tjp_avr_button_event(key_to_button_e(key), pressed);
}

static void dpad_event(uint8_t dpad_btn, bool pressed)
{
    tjp_avr_button_event(dpad_to_button_e(dpad_btn), pressed);
}

static int sdl_setup(void)
{
    if (SDL_Init(SDL_INIT_AUDIO | SDL_INIT_VIDEO) < 0) {
        return -1;
    }
    sdl_window = SDL_CreateWindow("TJP Emulator",
            SDL_WINDOWPOS_UNDEFINED,
            SDL_WINDOWPOS_UNDEFINED,
            OLED_WIDTH_PX * pixel_size,
            OLED_HEIGHT_PX * pixel_size,
            SDL_WINDOW_OPENGL | SDL_WINDOW_RESIZABLE);
    if (sdl_window == NULL) {
        return -1;
    }
    sdl_gl_context = SDL_GL_CreateContext(sdl_window);
    if (sdl_gl_context == NULL) {
        return -1;
    }

    SDL_AudioSpec desired, obtained;
    desired.freq = SOUND_RATE; /* Sampling rate */
    desired.format = AUDIO_U8; /* 8-bit unsigned audio */
    desired.channels = 1; /* Mono */
    desired.silence = 0x80;
    desired.samples = SOUND_BUFFER_SIZE;
    desired.callback = sdl_sound_callback;
    desired.userdata = NULL;
    if (SDL_OpenAudio(&desired, &obtained) < 0) {
        fprintf(stderr, "Sound isn't available.\n");
    }
    SDL_PauseAudio(0);

    return 0;
}

static int sdl_loop(void)
{
    SDL_Event event;
    while (SDL_PollEvent(&event) != 0) {
        switch (event.type) {
            case SDL_QUIT:
                return -1;
            case SDL_KEYDOWN:
                if (event.key.repeat) {
                    continue;
                }
                if (event.key.keysym.sym == SDLK_ESCAPE) {
                    return -1;
                }
                key_event(event.key.keysym.sym, true);
                break;
            case SDL_KEYUP:
                if (event.key.repeat) {
                    continue;
                }
                key_event(event.key.keysym.sym, false);
                break;
            case SDL_CONTROLLERBUTTONDOWN:
                dpad_event(event.cbutton.button, true);
                break;
            case SDL_CONTROLLERBUTTONUP:
                dpad_event(event.cbutton.button, false);
                break;
        }
    }
    return 0;
}

static void sdl_render_frame(int *pixels)
{
    glClear(GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT);
    glMatrixMode(GL_PROJECTION);
    glLoadIdentity();
    glOrtho(0, OLED_WIDTH_PX * pixel_size, 0, OLED_HEIGHT_PX * pixel_size, 0, 10);
    glTranslatef(0, OLED_HEIGHT_PX * pixel_size, 0);
    glScalef(pixel_size, -pixel_size, 1);
    glEnable(GL_BLEND);
    glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);

    glColor4ub(0x08, 0, 0x20, 0xFF);
    glBegin(GL_QUADS);
    glVertex2i(0, 0);
    glVertex2i(OLED_WIDTH_PX, 0);
    glVertex2i(OLED_WIDTH_PX, OLED_HEIGHT_PX);
    glVertex2i(0, OLED_HEIGHT_PX);

    for (int y = 0; y < OLED_HEIGHT_PX; y++) {
        for (int x = 0; x < OLED_WIDTH_PX; x++) {
            int p = *pixels++;
            glColor4ub(p & 0xFF, p >> 8 & 0xFF, p >> 16 & 0xFF, p >> 24 & 0xFF);
            glVertex2i(x, y);
            glVertex2i(x + 1, y);
            glVertex2i(x + 1, y + 1);
            glVertex2i(x, y + 1);
        }
    }
    glEnd();

    SDL_GL_SwapWindow(sdl_window);
}

static void sdl_sound_callback(void *unused, uint8_t *stream, int len)
{
    if (sound_buffer_available > SOUND_BUFFER_LIMIT) {
        sound_buffer_available = SOUND_BUFFER_LIMIT;
    }
    int obtained_len = tjp_avr_get_sound_buffer(sound_buffer + sound_buffer_available);
    sound_buffer_available += obtained_len;
    if (len < sound_buffer_available) {
        memcpy(stream, sound_buffer, len);
        sound_buffer_available -= len;
        memmove(sound_buffer, sound_buffer + len, sound_buffer_available);
    } else {
        memcpy(stream, sound_buffer, sound_buffer_available);
        len -= sound_buffer_available;
        memset(stream + sound_buffer_available, 0x80, len);
        sound_buffer_available = 0;
    }
}

void sdl_teardown(void)
{
    if (sdl_window != NULL) {
        SDL_DestroyWindow(sdl_window);
    }
    SDL_Quit();
}
