# Copyright (C) 2020 OBONO
# https://obono.hateblo.jp/
#
# This program is free software: you can redistribute it and/or modify
# it under the terms of the GNU General Public License as published by
# the Free Software Foundation, either version 3 of the License, or
# (at your option) any later version.
#
# This program is distributed in the hope that it will be useful,
# but WITHOUT ANY WARRANTY; without even the implied warranty of
# MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
# GNU General Public License for more details.
#
# You should have received a copy of the GNU General Public License
# along with this program.  If not, see <http://www.gnu.org/licenses/>.

##
##  Build JNI library
##
LOCAL_PATH := $(call my-dir)
include $(CLEAR_VARS)

# Source files to build
LIBELF_SRC_FILES := \
	elf32_checksum.c \
	elf32_fsize.c \
	elf32_getchdr.c \
	elf32_getehdr.c \
	elf32_getphdr.c \
	elf32_getshdr.c \
	elf32_newehdr.c \
	elf32_newphdr.c \
	elf32_offscn.c \
	elf32_updatenull.c \
	elf32_xlatetof.c \
	elf32_xlatetom.c \
	elf64_checksum.c \
	elf64_fsize.c \
	elf64_getchdr.c \
	elf64_getehdr.c \
	elf64_getphdr.c \
	elf64_getshdr.c \
	elf64_newehdr.c \
	elf64_newphdr.c \
	elf64_offscn.c \
	elf64_updatenull.c \
	elf64_xlatetof.c \
	elf64_xlatetom.c \
	elf_begin.c \
	elf_clone.c \
	elf_cntl.c \
	elf_compress.c \
	elf_end.c \
	elf_error.c \
	elf_fill.c \
	elf_flagdata.c \
	elf_flagehdr.c \
	elf_flagelf.c \
	elf_flagphdr.c \
	elf_flagscn.c \
	elf_flagshdr.c \
	elf_getarhdr.c \
	elf_getaroff.c \
	elf_getarsym.c \
	elf_getbase.c \
	elf_getdata.c \
	elf_getdata_rawchunk.c \
	elf_getident.c \
	elf_getphdrnum.c \
	elf_getscn.c \
	elf_getshdrnum.c \
	elf_getshdrstrndx.c \
	elf_gnu_hash.c \
	elf_hash.c \
	elf_kind.c \
	elf_memory.c \
	elf_ndxscn.c \
	elf_newdata.c \
	elf_newscn.c \
	elf_next.c \
	elf_nextscn.c \
	elf_rand.c \
	elf_rawdata.c \
	elf_rawfile.c \
	elf_readall.c \
	elf_scnshndx.c \
	elf_strptr.c \
	elf_version.c \
	gelf_checksum.c \
	gelf_fsize.c \
	gelf_getauxv.c \
	gelf_getchdr.c \
	gelf_getclass.c \
	gelf_getdyn.c \
	gelf_getehdr.c \
	gelf_getlib.c \
	gelf_getmove.c \
	gelf_getnote.c \
	gelf_getphdr.c \
	gelf_getrela.c \
	gelf_getrel.c \
	gelf_getshdr.c \
	gelf_getsym.c \
	gelf_getsyminfo.c \
	gelf_getsymshndx.c \
	gelf_getverdaux.c \
	gelf_getverdef.c \
	gelf_getvernaux.c \
	gelf_getverneed.c \
	gelf_getversym.c \
	gelf_newehdr.c \
	gelf_newphdr.c \
	gelf_offscn.c \
	gelf_update_auxv.c \
	gelf_update_dyn.c \
	gelf_update_ehdr.c \
	gelf_update_lib.c \
	gelf_update_move.c \
	gelf_update_phdr.c \
	gelf_update_rela.c \
	gelf_update_rel.c \
	gelf_update_shdr.c \
	gelf_update_sym.c \
	gelf_update_syminfo.c \
	gelf_update_symshndx.c \
	gelf_update_verdaux.c \
	gelf_update_verdef.c \
	gelf_update_vernaux.c \
	gelf_update_verneed.c \
	gelf_update_versym.c \
	gelf_xlate.c \
	gelf_xlatetof.c \
	gelf_xlatetom.c \
	libelf_crc32.c \
	libelf_next_prime.c \
	nlist.c

SIMAVR_SRC_FILES := \
	avr_acomp.c \
	avr_adc.c \
	avr_bitbang.c \
	avr_eeprom.c \
	avr_extint.c \
	avr_flash.c \
	avr_ioport.c \
	avr_lin.c \
	avr_spi.c \
	avr_timer.c \
	avr_twi.c \
	avr_uart.c \
	avr_usb.c \
	avr_watchdog.c \
	run_avr.c \
	sim_avr.c \
	sim_cmds.c \
	sim_core.c \
	sim_cycle_timers.c \
	sim_elf.c \
	sim_gdb.c \
	sim_hex.c \
	sim_interrupts.c \
	sim_io.c \
	sim_irq.c \
	sim_utils.c \
	sim_vcd_file.c \
	../cores/sim_tiny85.c \
	../cores/sim_tinyx5.c \
	../../examples/parts/ssd1306_virt.c

LOCAL_SRC_FILES := \
	$(patsubst %,elfutils/libelf/%,$(LIBELF_SRC_FILES)) \
	$(patsubst %,simavr/simavr/sim/%,$(SIMAVR_SRC_FILES)) \
	jni.c \
	tjp_avr.c

# Include JNI headers
LOCAL_C_INCLUDES += \
	$(LOCAL_PATH) \
	$(LOCAL_PATH)/elfutils \
	$(LOCAL_PATH)/elfutils/bionic-fixup \
	$(LOCAL_PATH)/elfutils/lib \
	$(LOCAL_PATH)/elfutils/libelf \
	$(LOCAL_PATH)/simavr/simavr/sim \
	$(LOCAL_PATH)/simavr/simavr/cores \
	$(LOCAL_PATH)/simavr/examples/parts

# C flags
LOCAL_CFLAGS += \
	-DHAVE_CONFIG_H \
	-D_GNU_SOURCE \
	-D_FILE_OFFSET_BITS=64 \
	-std=gnu99 \
	-O2 \
	-Wno-pointer-arith \
	-include $(LOCAL_PATH)/elfutils/bionic-fixup/AndroidFixup.h

# LD libraries
LOCAL_LDLIBS += \
	-lz -llog \

# Name of the library to build
LOCAL_MODULE := libTJPEmuNative

# Tell it to build a shared library
include $(BUILD_SHARED_LIBRARY)
