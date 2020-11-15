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

#include <stdbool.h>
#include <android/log.h>

#define OLED_WIDTH_PX   128
#define OLED_HEIGHT_PX  64
#define EEPROM_SIZE     512

#define FPS             30
#define FRAME_PERIOD_US (1000000 / FPS)

#define SOUND_RATE      32000
#define SOUND_BUFFER_SIZE 2048

#define LOG_TAG "TJPEmulator"
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)
#define LOGW(...) __android_log_print(ANDROID_LOG_WARN, LOG_TAG, __VA_ARGS__)
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGD(...) __android_log_print(ANDROID_LOG_DEBUG, LOG_TAG, __VA_ARGS__)
#define LOGV(...)

enum button_e {
    BTN_UP = 0,
    BTN_DOWN,
    BTN_LEFT,
    BTN_RIGHT,
    BTN_A,
    BTN_COUNT,
};

int tjp_avr_setup(const char *hex_file_path);
bool tjp_avr_get_eeprom(char *p_array);
bool tjp_avr_set_eeprom(const char *p_array);
bool tjp_avr_set_refresh_timing(bool b);
bool tjp_avr_button_event(enum button_e btn_e, bool pressed);
bool tjp_avr_loop(int *pixels);
int tjp_avr_get_sound_buffer(char *stream);
void tjp_avr_teardown(void);
