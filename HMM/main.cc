//
//  main.cpp
//  HMM
//
//  Created by NeroHu on 3/5/15.
//  Copyright (c) 2015 hthu. All rights reserved.
//

#include <fstream>
#include <map>
#include "hmm.h"


int main(int argc, const char * argv[]) {

	ObsVector obs;
	std::map<char, int> mapObject;

	for(int i = 0; i <26; i++) {
		mapObject.insert(std::pair<char, int>('a'+i, i));
	}
	mapObject.insert(std::pair<char, int>(' ', 26));

	std::ifstream infile;
	std::string data;

	infile.open("textA.txt");
	std::getline(infile,data);
	for (int i = 0; i < data.length(); i++) {
		obs.push_back(mapObject[data.at(i)]);
	}

	RowVector start;
	start.push_back(0.5);
	start.push_back(0.5);

	start.clear();
	Table t;
	start.push_back(0.49);
	start.push_back(0.51);
	t.push_back(start);
	start.clear();
	start.push_back(0.51);
	start.push_back(0.49);
	t.push_back(start);

	Table e;
	RowVector e1,e2;
	for (int i = 0; i < 27; i++) {
		if (i<=12) {
			e1.push_back(0.0370);
			e2.push_back(0.0371);
		}
		else if ( i > 12 && i <=25){
			e1.push_back(0.0371);
			e2.push_back(0.0370);
		}
		else{
			e1.push_back(0.0367);
			e2.push_back(0.0367);
		}
	}

	e.push_back(e1);
	e.push_back(e2);

	HMM hmm = HMM(start, t, e);
	hmm.setIter(600);
	hmm.Train(obs);
	hmm.printResult();

	return 0;
}
