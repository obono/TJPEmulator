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

#include <time.h>
#include <unistd.h>
#include <stdio.h>
#include <string.h>
#include <stdlib.h>

#include <sim_avr.h>
#include <sim_hex.h>
#include <sim_gdb.h>
#include <sim_time.h>

#define _AVR_IO_H_
#define __ASSEMBLER__
#include <avr/iotn85.h>
#include <sim_tinyx5.h>
#include <ssd1306_virt.h>

#include "tjp_avr.h"

/*---------------------------------------------------------------------------*/

#define F_CPU   16000000UL

#define AXIS_VOL_VCC    3300
#define AXIS_VOL_88K    2640
#define AXIS_VOL_33K    1980
#define AXIS_VOL_BOTH   1720

#define SSD1306_HEADER_COMMAND  0x00
#define SSD1306_HEADER_DATA     0x40

#define SOUND_VALUE_BASE    0x80
#define SOUND_VALUE_MAX     0x40

#define LUMA_COLOR(a)   ((uint8_t)(a) << 24 | 0xFFFFFF)
#define TRANSPARENT     0x00000000

enum {
    I2C_STATE_NONE = 0,
    I2C_STATE_BEGIN,
    I2C_STATE_SELECT,
    I2C_STATE_CMD,
    I2C_STATE_DATA,
};

/*---------------------------------------------------------------------------*/

extern void ssd1306_update_command_register(struct ssd1306_t *part);
extern void ssd1306_update_setting(struct ssd1306_t *part);

static struct avr_t *my_avr;
static struct ssd1306_t my_ssd1306;
static struct avr_irq_t *x_axis_irq, *y_axis_irq, *button_irq;
static uint32_t sound_sample_count, sound_high_count, sound_toggle_cycle, sound_buffer_count;
static uint8_t  sound_buffer[SOUND_BUFFER_SIZE];
static uint8_t  i2c_state;
static uint8_t  i2c_counter;
static uint8_t  i2c_data;
static bool     lumamap[OLED_HEIGHT_PX][OLED_WIDTH_PX];
static bool     sda_value;
static bool     scl_value;
static bool     button_pressed[BTN_COUNT];
static bool     yield, is_refresh_on_round;

/*---------------------------------------------------------------------------*/

#ifdef __ANDROID__
static void android_logger(avr_t * avr, const int level, const char * format, va_list ap)
{
    if (!avr || avr->log >= level) {
        int android_level = ANDROID_LOG_SILENT - level;
        __android_log_vprint(android_level, LOG_TAG, format, ap);
    }
}
#endif

static void avr_callback_sleep_sync(avr_t *avr, avr_cycle_count_t how_long)
{
    // do nothing
}

static avr_cycle_count_t frame_timer_callback(avr_t *avr, avr_cycle_count_t when, void *param)
{
    yield = true;
    return avr->cycle + avr_usec_to_cycles(avr, FRAME_PERIOD_US);
}

static avr_cycle_count_t sound_timer_callback(avr_t *avr, avr_cycle_count_t when, void *param)
{
    uint32_t cycles = when - sound_toggle_cycle;
    sound_sample_count += cycles;
    if (avr->data[PORTB] & (1 << PB4)) {
        sound_high_count += cycles;
    }
    if (sound_sample_count > 0 && sound_buffer_count < SOUND_BUFFER_SIZE) {
        sound_buffer[sound_buffer_count++] =
            SOUND_VALUE_BASE + sound_high_count * SOUND_VALUE_MAX / sound_sample_count;
    }
    sound_sample_count = 0;
    sound_high_count = 0;
    sound_toggle_cycle = when;
    return avr->cycle + (F_CPU / SOUND_RATE);
}

//------------------------------------------------------------------------------

static inline int get_fg_colour(bool invert, float opacity)
{
    return invert ? TRANSPARENT : LUMA_COLOR(256 * opacity);
}

static inline int get_bg_colour(bool invert, float opacity)
{
    return get_fg_colour(!invert, opacity);
}

static inline float contrast_to_opacity(uint8_t contrast)
{
    return 0.5f + contrast / 512.0f;
}

static void update_lumamap(struct ssd1306_t *ssd1306)
{
    for (int p = 0; p < SSD1306_VIRT_PAGES; p++) {
        for (int c = 0; c < SSD1306_VIRT_COLUMNS; c++) {
            uint8_t px_col = ssd1306->vram[p][c];
            for (int y = 0; y < 8; y++) {
                lumamap[p * 8 + y][c] = px_col & 0x1;
                px_col >>= 1;
            }
        }
    }
    ssd1306_set_flag(ssd1306, SSD1306_FLAG_DIRTY, 0);
}

static void render_screen(int *pixels, struct ssd1306_t *ssd1306)
{
    if (!ssd1306_get_flag(ssd1306, SSD1306_FLAG_DISPLAY_ON)) {
        return;
    }

    // Apply vertical and horizontal display mirroring
    int orig_x = 0, orig_y = 0;
    int vx = 1, vy = 1;
    if (ssd1306_get_flag(ssd1306, SSD1306_FLAG_SEGMENT_REMAP_0)) {
        orig_x = OLED_WIDTH_PX - 1; vx = -1;
    }
    if (ssd1306_get_flag(ssd1306, SSD1306_FLAG_COM_SCAN_NORMAL)) {
        orig_y = OLED_HEIGHT_PX - 1; vy = -1;
    }
    
    // Setup drawing colour
    bool invert = ssd1306_get_flag(ssd1306, SSD1306_FLAG_DISPLAY_INVERTED);
    float opacity = contrast_to_opacity(ssd1306->contrast_register);
    int bg_color = get_bg_colour(invert, opacity);
    int fg_color = get_fg_colour(invert, opacity);

    // Render screen
    for (int y = orig_y; y >= 0 && y < OLED_HEIGHT_PX; y += vy) {
        for (int x = orig_x; x >= 0 && x < OLED_WIDTH_PX; x += vx) {
            *pixels++ = lumamap[y][x] ? fg_color : bg_color;
        }
    }
}

static void ssd1306_write_command(ssd1306_t *part)
{
    if (!part->command_register) {
        // Single byte or start of multi-byte command
        ssd1306_update_command_register(part);
    } else {
        // Multi-byte command setting
        ssd1306_update_setting(part);
    }
}

static void ssd1306_write_data(ssd1306_t *part)
{
    part->vram[part->cursor.page][part->cursor.column] = part->spi_data;
    ssd1306_set_flag(part, SSD1306_FLAG_DIRTY, 1);

    // Scroll the cursor
    if (++(part->cursor.column) >= SSD1306_VIRT_COLUMNS) {
        part->cursor.column = 0;
        if ( part->addr_mode == SSD1306_ADDR_MODE_HORZ &&
                (++(part->cursor.page) >= SSD1306_VIRT_PAGES)) {
            part->cursor.page = 0;
            if (is_refresh_on_round) {
                update_lumamap(&my_ssd1306);
            }
        }
    }
}

static void ssd1306_receive_byte(uint8_t data)
{
    if (i2c_state == I2C_STATE_BEGIN) {
        if (((data >> 1) & SSD1306_I2C_ADDRESS_MASK) == SSD1306_I2C_ADDRESS) {
            i2c_state = I2C_STATE_SELECT;
        } else {
            i2c_state = I2C_STATE_NONE;
        }
    } else if (i2c_state == I2C_STATE_SELECT) {
        switch (data) {
        case SSD1306_HEADER_COMMAND:
            i2c_state = I2C_STATE_CMD;
            break;
        case SSD1306_HEADER_DATA:
            i2c_state = I2C_STATE_DATA;
            break;
        default:
            i2c_state = I2C_STATE_NONE;
            break;
        }
    } else if (i2c_state == I2C_STATE_CMD) {
        my_ssd1306.spi_data = data;
        ssd1306_write_command(&my_ssd1306);
    } else if (i2c_state == I2C_STATE_DATA) {
        my_ssd1306.spi_data = data;
        ssd1306_write_data(&my_ssd1306);
    }
}

//------------------------------------------------------------------------------

static void sda_hook(struct avr_irq_t *irq, uint32_t value, void *param)
{
    sda_value = value;
    if (scl_value) {
        if (!sda_value /*&& i2c_state == I2C_STATE_NONE*/) {
            LOGV("start I2C\n");
            my_avr->data[USISR] |= 1 << USISIF;
            i2c_state = I2C_STATE_BEGIN;
            i2c_counter = 0;
        } else if (sda_value && i2c_state != I2C_STATE_NONE){
            LOGV("end I2C\n");
            my_avr->data[USISR] |= 1 << USIPF;
            i2c_state = I2C_STATE_NONE;
        }
    }
}

static void scl_hook(struct avr_irq_t *irq, uint32_t value, void *param)
{
    scl_value = value;
    if (i2c_state != I2C_STATE_NONE) {
        if (scl_value) {
            if (i2c_counter < 8) {
                i2c_data = i2c_data << 1 | sda_value;
            }
            if (++i2c_counter == 9) {
                ssd1306_receive_byte(i2c_data);
                i2c_counter = 0;
            }
            my_avr->data[PINB] &= ~(1 << PINB0); // ack (clear SDA)
        }
    }
}

static void sound_hook(struct avr_irq_t *irq, uint32_t value, void *param)
{
    uint32_t cycles = my_avr->cycle - sound_toggle_cycle;
    sound_sample_count += cycles;
    if (!value) { // H -> L
        sound_high_count += cycles;
    }
    sound_toggle_cycle = my_avr->cycle;

}

static void write_hook(struct avr_t *avr, avr_io_addr_t addr, uint8_t value, void *param)
{
    if (addr == USICR) {
        if (value & (1 << USITC)) {
            avr->data[PORTB] ^= 1 << PB2; // toggle SCL
            uint8_t reg = avr->data[USISR];
            reg = (reg & 0xF0) | (((reg & 0x0F) + 1) & 0x0F);
            if ((reg & 0x0F) == 0) {
                reg |= 1 << USIOIF;
            }
            avr->data[USISR] = reg;
        }
        value &= 0xFC;
    } else if (addr == USISR) {
        if (value == 0xF0) {
            ssd1306_receive_byte(avr->data[USIDR]);
        } else if (value == 0xFE) {
            if (i2c_state != I2C_STATE_NONE) {
                avr->data[USIDR] &= ~0x01; // ack
            }
        }
        uint8_t reg = avr->data[USISR];
        value = (reg & ~((value & 0xE0) | 0x0F)) | (value & 0x0F);
    }
    avr->data[addr] = value;
}

//------------------------------------------------------------------------------

int tjp_avr_setup(const char *hex_file_path)
{
#ifdef __ANDROID__
    avr_global_logger_set(android_logger);
#endif
    my_avr = avr_make_mcu_by_name("attiny85");
    if (!my_avr) {
        LOGE("Failed to make AVR\n");
        return -1;
    }
    avr_init(my_avr);

    {
        /* Load .hex and setup program counter */
        uint32_t boot_base, boot_size;
        uint8_t *boot = read_ihex_file(hex_file_path, &boot_size, &boot_base);
        if (!boot) {
            avr_terminate(my_avr);
            LOGE("Unable to load \"%s\"\n", hex_file_path);
            return -1;
        }
        memcpy(my_avr->flash + boot_base, boot, boot_size);
        free(boot);
        my_avr->pc = boot_base;
        my_avr->codeend = my_avr->flashend;
    }

    /* More simulation parameters */
    my_avr->log = LOG_DEBUG; // LOG_NONE
    my_avr->frequency = F_CPU;
    my_avr->vcc = AXIS_VOL_VCC;
    my_avr->sleep = avr_callback_sleep_sync;
    my_avr->run_cycle_limit = avr_usec_to_cycles(my_avr, FRAME_PERIOD_US);

    /* Setup display controller */
    ssd1306_init(my_avr, &my_ssd1306, OLED_WIDTH_PX, OLED_HEIGHT_PX);
    //ssd1306_connect_twi(&my_ssd1306, &ssd1306_wiring);
    avr_irq_register_notify(avr_io_getirq(my_avr, AVR_IOCTL_IOPORT_GETIRQ('B'), 0), sda_hook, NULL);
    avr_irq_register_notify(avr_io_getirq(my_avr, AVR_IOCTL_IOPORT_GETIRQ('B'), 2), scl_hook, NULL);
    avr_register_io_write(my_avr, USICR, write_hook, NULL);
    avr_register_io_write(my_avr, USISR, write_hook, NULL);
    i2c_state = I2C_STATE_NONE;
    i2c_counter = 0;
    i2c_data = 0;
    memset(lumamap, 0, sizeof(lumamap));
    sda_value = scl_value = false;

    /* Setup buttons */
    for (int btn_idx = 0; btn_idx < BTN_COUNT; btn_idx++) {
        button_pressed[btn_idx] = false;
    }
    button_irq = avr_io_getirq(my_avr, AVR_IOCTL_IOPORT_GETIRQ('B'), 1);
    x_axis_irq = avr_io_getirq(my_avr, AVR_IOCTL_ADC_GETIRQ, ADC_IRQ_ADC0);
    y_axis_irq = avr_io_getirq(my_avr, AVR_IOCTL_ADC_GETIRQ, ADC_IRQ_ADC3);
    avr_raise_irq(button_irq, 1);
    avr_raise_irq(x_axis_irq, AXIS_VOL_VCC);
    avr_raise_irq(y_axis_irq, AXIS_VOL_VCC);

    /* Setup speaker */
    sound_sample_count = 0;
    sound_high_count = 0;
    sound_toggle_cycle = 0;
    sound_buffer_count = 0;
    avr_irq_register_notify(avr_io_getirq(my_avr, AVR_IOCTL_IOPORT_GETIRQ('B'), 4), sound_hook, NULL);

    /* Setup timers */
    avr_cycle_timer_register_usec(my_avr, FRAME_PERIOD_US, frame_timer_callback, NULL);
    avr_cycle_timer_register(my_avr, F_CPU / SOUND_RATE, sound_timer_callback, NULL);

    LOGI("Setup AVR\n");
    return 0;
}

bool tjp_avr_get_eeprom(char *p_array)
{
    if (!my_avr) {
        return false;
    }
    struct mcu_t *mcu = (struct mcu_t *) my_avr;
    memcpy(p_array, mcu->eeprom.eeprom, mcu->eeprom.size);
    return true;
}

bool tjp_avr_set_eeprom(const char *p_array)
{
    if (!my_avr) {
        return false;
    }
    struct mcu_t *mcu = (struct mcu_t *) my_avr;
    memcpy(mcu->eeprom.eeprom, p_array, mcu->eeprom.size);
    return true;
}

bool tjp_avr_set_refresh_timing(bool b)
{
    is_refresh_on_round = b;
    return true;
}

bool tjp_avr_button_event(enum button_e btn_e, bool pressed)
{
    if (!my_avr || btn_e < 0 || btn_e >= BTN_COUNT || button_pressed[btn_e] == pressed) {
        return false;
    }
    button_pressed[btn_e] = pressed;
    if (btn_e == BTN_LEFT || btn_e == BTN_RIGHT) {
        uint16_t vol;
        if (!button_pressed[BTN_LEFT] && !button_pressed[BTN_RIGHT]) {
            vol = AXIS_VOL_VCC;
        } else if (button_pressed[BTN_LEFT] && !button_pressed[BTN_RIGHT]) {
            vol = AXIS_VOL_88K;
        } else if (!button_pressed[BTN_LEFT] && button_pressed[BTN_RIGHT]) {
            vol = AXIS_VOL_33K;
        } else {
            vol = AXIS_VOL_BOTH;
        }
        avr_raise_irq(x_axis_irq, vol);
    } else if (btn_e == BTN_UP || btn_e == BTN_DOWN) {
        uint16_t vol;
        if (!button_pressed[BTN_DOWN] && !button_pressed[BTN_UP]) {
            vol = AXIS_VOL_VCC;
        } else if (button_pressed[BTN_DOWN] && !button_pressed[BTN_UP]) {
            vol = AXIS_VOL_88K;
        } else if (!button_pressed[BTN_DOWN] && button_pressed[BTN_UP]) {
            vol = AXIS_VOL_33K;
        } else {
            vol = AXIS_VOL_BOTH;
        }
        avr_raise_irq(y_axis_irq, vol);
    } else if (btn_e == BTN_A) {
        avr_raise_irq(button_irq, !pressed);
    }
    return true;
}

bool tjp_avr_loop(int *pixels)
{
    if (!my_avr) {
        return false;
    }

    /*  Forward CPU  */
    yield = false;
    while (!yield) {
        my_avr->run(my_avr);
        int state = my_avr->state;
        if (state == cpu_Done || state == cpu_Crashed) {
            break;
        }
    }

    /*  Refesh Screen  */
    if (!is_refresh_on_round) {
        update_lumamap(&my_ssd1306);
    }
    render_screen(pixels, &my_ssd1306);

    return true;
}

int tjp_avr_get_sound_buffer(char *stream)
{
    if (!my_avr) {
        return 0;
    }

    int ret = sound_buffer_count;
    memcpy(stream, sound_buffer, sound_buffer_count);
    sound_buffer_count = 0;
    return ret;
}

void tjp_avr_teardown(void)
{
    if (my_avr) {
        avr_terminate(my_avr);
        my_avr = NULL;
        LOGI("Terminate AVR\n");
    }
}
