#include <sstream>
#include <GL/glew.h>
#include <GL/gl.h>
#include <GL/glu.h>
#include <SDL/SDL.h>
#include <SDL/SDL_ttf.h>
#include <SDL/SDL_image.h>
#include <math.h>
#include <string>
#include <iostream>
#include <sstream>
#include <fstream>
#include <vector>
#include <stdlib.h>
#include <stdio.h>
#include <math.h>
#include <sys/time.h>
#include <sys/types.h>
#include <sys/socket.h>
#include <netinet/in.h>
#include <arpa/inet.h>
#include <netdb.h>

#include "src/keyboard.h"
#include "src/joystick.h"
#include "src/timer.h"

int main(int argc, char* argv[]) {

	if (argc != 3) {
		std::cout << "usage: " << argv[0] << " ipadrr port" << std::endl;
		return 1;
	}
	
	bool active = true;
	
	cKeyboard kb;
	cJoystick js;
	cTimer t0; double elapsed0;
  
	int s, rc, len, port, p;
	char host[64], str[64], buf[512];
	struct hostent *hp;
	struct sockaddr_in sin;
	
	hp = gethostbyname(argv[1]);
	if (hp == NULL) {
		std::cout << "host not found" << std::endl;
		return 2;
	}
	port = atoi(argv[2]);
	
	s = socket(AF_INET, SOCK_STREAM, 0);
	if (s < 0) {
		std::cout << "socket error" << std::endl;
		return 2;
	}
	
	sin.sin_family = AF_INET;
	sin.sin_port = htons(port);
	memcpy(&sin.sin_addr, hp->h_addr_list[0], hp->h_length);
	
	rc = connect(s, (struct sockaddr *)&sin, sizeof(sin));
	if (rc < 0) {
		std::cout << "connect error" << std::endl;
		return 3;
	}
	
	unsigned char state[8]={'0','0','0','0','0','0','0','0'}, new_state[8]={'0','0','0','0','0','0','0','0'};
	bool change = true;

	while (active) {

		if (kb.getKeyState(KEY_ESC)) active = false;
		
		for (int i = 0; i < 4; i++) {
			int btn = i+4; // up down left right
			if (js.buttonPressed(btn)) new_state[i] = '1';
			else new_state[i] = '0';
		}
		for (int i = 0; i < 4; i++) {
			int btn = i+12; // triangle circle x square
			if (js.buttonPressed(btn)) new_state[i+4] = '1';
			else new_state[i+4] = '0';
		}
		
		for (int i = 0; i < 8; i++) {
			if (state[i] != new_state[i]) {
				change = true;
				break;
			}
		}

		if (change) {
			std::cerr << '\r';
			for (int i = 0; i < 8; i++) {
				state[i] = new_state[i];
				std::cerr << state[i] << ' ';
			}

			send(s, state, 8, 0);
			change = false;
		}
	}

	close(s);
	
	return 0;
}