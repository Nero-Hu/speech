//
//  main.cc
//  recognizer
//
//  Created by NeroHu on 3/6/15.
//  Copyright (c) 2015 hthu. All rights reserved.
//

#include "fenonic.h"
#include <unistd.h>

//default iteration times
static const int DEFAULT_ITER = 5;

int main(int argc, char **argv) {
	//First get running options
	bool b_runDev = true;
	bool b_contrast = false;
	//default iteration times
	int iter = DEFAULT_ITER;
	int c;
	while ((c = getopt(argc, argv, "si:ch")) != -1) {
		switch (c) {
			case 's':
				b_runDev = true;
				std::cout << "Will run on test data set." << std::endl;
				break;
			case 'i':
				iter = atoi(optarg);
				std::cout << "Iteration has been set to " << iter << " times." << std::endl;
				break;
			case 'c':
				b_contrast = true;
				break;
			case 'h':
			default:
				std::cout << "Usage:\n-s to run on test data (default true) \n-i iter to define iteration times (default 5 iter) \n-c use contrastive model to train (default false)\
				" << std::endl;
				exit(0);
		}
	}
	unsigned long start_s= clock();	
	Fenonic feno = Fenonic(b_contrast);
	std::cout << "Start training... " << std::endl;
	//Normal mode
	if (!b_contrast) {
		for (int i = 0; i < iter; i++) {
			std::cout << "Iteration " << i+1 << std::endl;
			feno.Train();
			feno.PrintLogLikelihood();
		}
	}
	//Contrasts system
	else{
		unsigned long dStart = clock();
		int nStar = 0;
		float acc = 0.0;
		float prevAcc = 1.0;
		while (acc != prevAcc) {
			prevAcc = acc;
			feno.Train();
			acc = feno.ComputeAcc();
			nStar++;
		}
		std::cout << "Decide runtime : " <<
		(clock()-dStart)/double(CLOCKS_PER_SEC) <<"s." << std::endl;
		std::cout << "=========================" << std::endl;
		
		std::cout << "N star is : " << nStar << std::endl;
		start_s = clock();
		//Optimal iteration counts
		Fenonic newFeno = Fenonic(false);
		for (int i = 0; i < nStar; i++) {
			std::cout << "Iteration " << i+1 << std::endl;
			newFeno.Train();
			newFeno.PrintLogLikelihood();
		}
	}
	
	unsigned long stop_s=clock();
	std::cout << "Training runtime : " << (stop_s-start_s)/double(CLOCKS_PER_SEC) <<"s." << std::endl;
	std::cout << "=========================" << std::endl;
	start_s= clock();
	//Run on dev, and if not contrast setting
	if (b_runDev && !b_contrast) {
		feno.Predict();
		stop_s=clock();
		std::cout << "Testing runtime : " << (stop_s-start_s)/double(CLOCKS_PER_SEC) <<"s." << std::endl;
	}
	return 0;
}
