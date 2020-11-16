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

target = tjpemulator

simavr-repo = ./simavr
simavr = ${simavr-repo}/simavr
simavr-parts = ${simavr-repo}/examples/parts

IPATH = .
IPATH += ${simavr-parts}
IPATH += ${simavr}/include
IPATH += ${simavr}/sim
IPATH += ${simavr}/cores

VPATH = .
VPATH += ${simavr-repo}/examples/parts

include ${simavr-repo}/examples/Makefile.opengl

all: obj libsimavr ${target}

libsimavr:
	make -C ${simavr} libsimavr

obj:
	mkdir -p ${OBJ}

include ${simavr-repo}/Makefile.common
${warning ${LDFLAGS}}
LDFLAGS := -lGL -lGLU -lglut -lm -lelf -lSDL2 ${simavr}/${OBJ}/libsimavr.a

board = ${OBJ}/${target}.elf

${board} : ${OBJ}/ssd1306_virt.o
${board} : ${OBJ}/tjp_sdl.o
${board} : ${OBJ}/tjp_avr.o

${target}: ${board}
	cp ${board} $@
	@echo $@ done

clean: clean-${OBJ}
	make -C ${simavr-repo} clean
	rm -rf ${target} *.a *.vcd .*.swo .*.swp .*.swm .*.swn

