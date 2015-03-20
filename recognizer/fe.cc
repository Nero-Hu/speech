//
//  main.cc
//  recognizer
//
//  Created by NeroHu on 3/6/15.
//  Copyright (c) 2015 hthu. All rights reserved.
//

#include "fenonic.h"

int main(int argc, const char * argv[]) {
	unsigned long start_s= clock();
	std::cout << "Start training... " << std::endl;
	Fenonic feno = Fenonic();
	for (int i = 0; i < 8; i++) {
		feno.Train();
		feno.PrintLogLikelihood();
	}
	
	unsigned long stop_s=clock();
	std::cout << "runtime: " << (stop_s-start_s)/double(CLOCKS_PER_SEC)*1000 << std::endl;
	return 0;
}
