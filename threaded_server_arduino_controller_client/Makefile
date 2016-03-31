all: main.cc lib/keyboard.o lib/joystick.o lib/timer.o
	g++ main.cc lib/keyboard.o lib/joystick.o lib/timer.o -o bin/main -L/usr/lib -lpthread -lrt

lib/timer.o: src/timer.h src/timer.cc
	g++ src/timer.cc -c -o lib/timer.o

lib/joystick.o: src/joystick.h src/joystick.cc
	g++ src/joystick.cc -c -o lib/joystick.o

lib/keyboard.o: src/keyboard.h src/keyboard.cc
	g++ src/keyboard.cc -c -o lib/keyboard.o

clean:
	@rm -f *~ src/*~ lib/* bin/*