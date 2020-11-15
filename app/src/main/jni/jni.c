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
#include "tjp_avr.h"
#include "com_obnsoft_tjpemu_Native.h"

/*
 * Class:     com_obnsoft_tjpemu_Native
 * Method:    setup
 * Signature: (Ljava/lang/String;Z)Z
 */
JNIEXPORT jboolean JNICALL Java_com_obnsoft_tjpemu_Native_setup(
        JNIEnv *env, jclass obj, jstring js_path) {
    int ret;
    const char *path = (*env)->GetStringUTFChars(env, js_path, NULL);
    ret = tjp_avr_setup(path);
    (*env)->ReleaseStringUTFChars(env, js_path, path);
    return !ret;
}

/*
 * Class:     com_obnsoft_tjpemu_Native
 * Method:    getEeprom
 * Signature: ([B)Z
 */
JNIEXPORT jboolean JNICALL Java_com_obnsoft_tjpemu_Native_getEeprom(
        JNIEnv *env, jclass obj, jbyteArray jbyte_array) {
    jboolean ret;
    jbyte *p_array = (*env)->GetByteArrayElements(env, jbyte_array, &ret);
    int array_len = (*env)->GetArrayLength(env, jbyte_array);

    if (array_len >= EEPROM_SIZE) {
        ret = tjp_avr_get_eeprom((char *) p_array);
    } else {
        ret = JNI_FALSE;
    }

    (*env)->ReleaseByteArrayElements(env, jbyte_array, p_array, 0);
    return ret;
}

/*
 * Class:     com_obnsoft_tjpemu_Native
 * Method:    setEeprom
 * Signature: ([B)Z
 */
JNIEXPORT jboolean JNICALL Java_com_obnsoft_tjpemu_Native_setEeprom(
        JNIEnv *env, jclass obj, jbyteArray jbyte_array) {
    jboolean ret;
    jbyte *p_array = (*env)->GetByteArrayElements(env, jbyte_array, &ret);
    int array_len = (*env)->GetArrayLength(env, jbyte_array);

    if (array_len >= EEPROM_SIZE) {
        ret = tjp_avr_set_eeprom((const char *) p_array);
    } else {
        ret = JNI_FALSE;
    }

    (*env)->ReleaseByteArrayElements(env, jbyte_array, p_array, 0);
    return ret;
}

/*
 * Class:     com_obnsoft_tjpemu_Native
 * Method:    setRefreshTiming
 * Signature: (Z)Z
 */
JNIEXPORT jboolean JNICALL Java_com_obnsoft_tjpemu_Native_setRefreshTiming(
        JNIEnv *env, jclass obj, jboolean is_on_round) {
    jboolean ret = tjp_avr_set_refresh_timing(is_on_round);
    return ret;
}

/*
 * Class:     com_obnsoft_tjpemu_Native
 * Method:    buttonEvent
 * Signature: (IZ)Z
 */
JNIEXPORT jboolean JNICALL Java_com_obnsoft_tjpemu_Native_buttonEvent(
        JNIEnv *env, jclass obj, jint key, jboolean is_press) {
    return tjp_avr_button_event((enum button_e) key, is_press);
}

/*
 * Class:     com_obnsoft_tjpemu_Native
 * Method:    loop
 * Signature: ([I)Z
 */
JNIEXPORT jboolean JNICALL Java_com_obnsoft_tjpemu_Native_loop(
        JNIEnv *env, jclass obj, jintArray jint_array) {
    jboolean ret;
    jint *p_array = (*env)->GetIntArrayElements(env, jint_array, &ret);
    int array_len = (*env)->GetArrayLength(env, jint_array);

    if (array_len >= OLED_WIDTH_PX * OLED_HEIGHT_PX) {
        ret = tjp_avr_loop(p_array);
    } else {
        ret = JNI_FALSE;
    }

    (*env)->ReleaseIntArrayElements(env, jint_array, p_array, 0);
    return ret;
}

/*
 * Class:     com_obnsoft_tjpemu_Native
 * Method:    getSoundBuffer
 * Signature: ([B)Z
 */
JNIEXPORT jint JNICALL Java_com_obnsoft_tjpemu_Native_getSoundBuffer(
        JNIEnv *env, jclass obj, jbyteArray jbyte_array) {
    jint ret;
    jboolean dummy;
    jbyte *p_array = (*env)->GetByteArrayElements(env, jbyte_array, &dummy);
    int array_len = (*env)->GetArrayLength(env, jbyte_array);

    if (array_len >= SOUND_BUFFER_SIZE) {
        ret = tjp_avr_get_sound_buffer((char *) p_array);
    } else {
        ret = 0;
    }

    (*env)->ReleaseByteArrayElements(env, jbyte_array, p_array, 0);
    return ret;
}

/*
 * Class:     com_obnsoft_tjpemu_Native
 * Method:    teardown
 * Signature: ()V
 */
JNIEXPORT void JNICALL Java_com_obnsoft_tjpemu_Native_teardown(
        JNIEnv *env, jclass obj) {
    tjp_avr_teardown();
}
