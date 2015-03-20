//
//  fenonic.h
//  recognizer
//
//  Created by NeroHu on 3/6/15.
//  Copyright (c) 2015 hthu. All rights reserved.
//

#ifndef recognizer_fenonic_h
#define recognizer_fenonic_h

#include <iostream>
#include <fstream>
#include <unordered_map>
#include <deque>
#include <cmath>
#include "hmm.h"

static const std::string LBLNAMES = "clsp.lblnames";
static const std::string TRNSCR = "clsp.trnscr";
static const std::string TRNWAV = "clsp.trnwav";
static const std::string TRNLBLS = "clsp.trnlbls";
static const std::string ENDPT = "clsp.endpts";

//Pair of one training src, like <wav file name, <vector<Labels>, <startLoc, endLoc>>>
//e.g: <0005.wav, <HH, HM ..., >, <48,96>>>
typedef std::pair<std::string, std::pair<StringVec, std::pair<int, int> > > TPair;
//Training Map
typedef std::map<std::string, std::vector<TPair> > TMap;

//HMM Map
typedef std::map<std::string, HMM> HMap;

//baseform Map
typedef std::unordered_map<std::string, StringVec> Baseform;

class Fenonic {
	
	unsigned long start_s, stop_s;
	
	void constructTrnPair() {
		std::string delimiter = " ";
		
		std::ifstream trnscr, trnwav, trnlbls, trnEndpt;
		std::string strScr, strWav, strLbls, strEndpt;
		
		trnscr.open(TRNSCR);
		trnwav.open(TRNWAV);
		trnlbls.open(TRNLBLS);
		trnEndpt.open(ENDPT);
		//Trim first line
		trnscr >> strScr; trnwav >> strWav; std::getline(trnlbls,strLbls); trnEndpt >> strEndpt;
		//Load data
		for (int i = 0;trnscr >> strScr && trnwav >> strWav &&
			 std::getline(trnlbls,strLbls); i++) {
			//If first time, make a new vector
			if (trnPair.find(strScr) == trnPair.end()) {
				std::vector<TPair> pair;
				StringVec fenem;
				
				size_t pos = 0;
				std::string token;
				for (int j = 0; (pos = strLbls.find(delimiter)) != std::string::npos; j++) {
					token = strLbls.substr(0, pos);
					fenem.push_back(token);
					strLbls.erase(0, pos + delimiter.length());
				}
				
				int start, end;
				trnEndpt >> start;
				trnEndpt >> end;
				pair.push_back(std::make_pair(strWav,
											  std::make_pair(fenem, std::make_pair(start, end))));
				trnPair.insert(std::make_pair(strScr, pair));
				//Construct a baseform for each word
				StringVec base;
				base.push_back(SIL);
				for (int i = start; i < end-1; i++) {
					base.push_back(fenem[i]);
				}
				base.push_back(SIL);
				_baseform.insert(std::make_pair(strScr, base));
			}
			//If key exists just push back
			else {
				StringVec fenem;
				
				size_t pos = 0;
				std::string token;
				for (int j = 0; (pos = strLbls.find(delimiter)) != std::string::npos; j++) {
					token = strLbls.substr(0, pos);
					fenem.push_back(token);
					strLbls.erase(0, pos + delimiter.length());
				}
				
				int start, end;
				trnEndpt >> start;
				trnEndpt >> end;
				//push back to existing vector
				trnPair.find(strScr)->second.push_back(std::make_pair(strWav,
												std::make_pair(fenem, std::make_pair(start, end))));
			}
		}
	}
	
public:
	//Mapping the fenoms and their HMM
	HMap fenoMap;
	//Training pair <word, Vector<TPair>
	TMap trnPair;
	//Baseform for words
	Baseform _baseform;
	//For current word baseform
	StringVec curBaseform;
	
	//alpha, beta for trellis
	std::vector<RowVec> _alpha;
	std::deque<ReRowVec> _beta;
	RowVec _coef;
	//total states for a trellis
	float sumStates;
	//Loglikelihood
	std::vector<float> _logProb;
	
	Fenonic() {
		std::ifstream infile;
		std::string data;
		
		infile.open(LBLNAMES);
		//Skip first line
		infile >> data;
		//Initialize map pair for fenoms and its corresponding HMM
		for (int i = 0 ; infile >> data; i++) {
			fenoMap.insert(std::make_pair(data, HMM(data,i)));
			HIdxMap.insert(std::make_pair(data, i));
		}
		fenoMap.insert(std::make_pair(SIL, HMM(SIL,FENO_SIZE-1)));
		HIdxMap.insert(std::make_pair(SIL, FENO_SIZE-1));
		
		//construct train pair
		constructTrnPair();
	}
	
	void Train() {
		_logProb.clear();
		for (TMap::iterator it = trnPair.begin(); it != trnPair.end(); it++) {
			curBaseform = _baseform.find(it->first)->second;
			std::cout << "Training word : " << it->first << std::endl;
			//For each word training instance
			for (std::vector<TPair>::iterator ins = it->second.begin(); ins != it->second.end(); ins++) {
				//Clean alpha, beta and coefficient
				_alpha.clear();
				_beta.clear();
				_coef.clear();
				//Put alpha0, set start state to be 1
				RowVec alpha0;
				//Reset sum states for each instance
				sumStates = 0.0;
				for (StringVec::iterator fe = curBaseform.begin(); fe != curBaseform.end(); fe++) {
					for (int i = 0; i < fenoMap.find(*fe)->second._numOfStates; i++) {
						alpha0.push_back(1.0);
						sumStates += 1.0;
					}
				}
//				alpha0[0] = 1.0;
				//Uniform start probability
				for (RowVec::iterator a0 = alpha0.begin(); a0 != alpha0.end(); a0++) {
					*a0 /= sumStates;
				}
//				alpha0[0] = 1.0;
				_alpha.push_back(alpha0);
				CollectCounts(ins->second.first);
			}
		}
		
		//Update transition and emission probabilities
		for (Baseform::iterator base = _baseform.begin(); base != _baseform.end(); base++) {
			StringVec fenoms = base->second;
			//Could be duplicate update, but we don't reset counts here, so results will be fine
			for (StringVec::iterator fe = fenoms.begin(); fe != fenoms.end(); fe++) {
				fenoMap.find(*fe)->second.Update();
//				fenoMap.find(*fe)->second.PrintHMM();
			}
		}
		
		//Reset counts
		for (HMap::iterator fe = fenoMap.begin(); fe != fenoMap.end(); fe++) {
			//Reset counts
			fe->second.ResetCounts();
		}
		
	}
	
	void CollectCounts(StringVec obs) {
		float logProb = 0.0;
		RowVec predAlpha = _alpha[0];
		_coef.push_back(1.0);
		//Alpha and beta vector for each instance
		for (StringVec::iterator it = obs.begin(); it != obs.end(); it++) {
			RowVec alphaVec;
			//for baseform trellis, compute alpha
			int feLoc = 0;
			float sum = 0.0;
			for (StringVec::iterator fe = curBaseform.begin(); fe != curBaseform.end(); fe++) {
				HMM hmm = fenoMap.find(*fe)->second;
				hmm.ComputeForward(predAlpha, feLoc, &alphaVec, *it);
				feLoc += hmm._numOfStates;
			}
			//Normalize alpha
			for (RowVec::iterator a = alphaVec.begin(); a != alphaVec.end(); a++) {
				sum += *a;
			}
			for (RowVec::iterator a = alphaVec.begin(); a != alphaVec.end(); a++) {
				*a /= sum;
			}
			//Push to coefficient
			logProb += log2f(sum);
			_coef.push_back(sum);
			predAlpha = alphaVec;
			_alpha.push_back(alphaVec);
		}
		_logProb.push_back(logProb);
		//Initialize beta with 1/coefficient
		start_s = clock();
		ReRowVec successorBeta;
		RowVec lastAlpha = _alpha.back();
		float coef = _coef.back();
		if (_logProb.size() == 702) {
			std::cout <<  coef << std::endl;
		}
		for (RowVec::iterator la = lastAlpha.begin(); la != lastAlpha.end(); la++) {
			successorBeta.push_back(1.0/coef);
		}
		_beta.push_front(successorBeta);
		//Compute beta
		for (int i = (int)obs.size()-1; i >=0 ; i--) {
			ReRowVec betaVec;
			//for baseform trellis, compute beta
			int endLoc = sumStates-1;
			//Loop from back, since the beta from successor should be carried
			for (StringVec::reverse_iterator fe = curBaseform.rbegin(); fe != curBaseform.rend(); fe++) {
				HMM hmm = fenoMap.find(*fe)->second;
				hmm.ComputeBackwardAndColCounts(&_alpha[i], _coef[i],
												successorBeta, endLoc, &betaVec, obs[i]);
				endLoc -= hmm._numOfStates;
			}
			successorBeta = betaVec;
			_beta.push_front(betaVec);
		}
	}
	
	void PrintLogLikelihood() {
		float sumP = 0.0;
		for (std::vector<float>::iterator log = _logProb.begin(); log != _logProb.end(); log++) {
			sumP += *log;
		}
		sumP /= 798;
		std::cout << "Log-prob : " << sumP << std::endl;
	}
};

#endif
