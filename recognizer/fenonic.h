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
#include <set>
#include "hmm.h"

static const std::string LBLNAMES = "clsp.lblnames";
static const std::string TRNSCR = "clsp.trnscr";
static const std::string TRNWAV = "clsp.trnwav";
static const std::string TRNLBLS = "clsp.trnlbls";
static const std::string ENDPT = "clsp.endpts";

static const std::string DEVLBLS = "clsp.devlbls";
static const std::string DEVWAV = "clsp.devwav";


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
	
	//Mapping the fenoms and their HMM
	HMap fenoMap;
	//Training pair <word, Vector<TPair>
	TMap trnPair;
	//Contrast system pair using 20-80 splits
	TMap cstTrnPair;
	TMap cstTstPair;
	//Baseform for words
	Baseform _baseform;
	//For current word baseform
	StringVec curBaseform;
	//For dev labels
	std::vector<StringVec> devLabelVec;
	StringVec devWavVec;
	
	bool _isContrast;
	
	//total frames
	float totalFrames;
	
	void ConstructData() {
		trnPair.clear();
		cstTrnPair.clear();
		cstTstPair.clear();
		std::string delimiter = " ";
		
		std::ifstream trnscr, trnwav, trnlbls, trnEndpt, devLbls, devWav;
		std::string strScr, strWav, strLbls, strEndpt, strDevLbls, strDevWav;
		
		trnscr.open(TRNSCR);
		trnwav.open(TRNWAV);
		trnlbls.open(TRNLBLS);
		trnEndpt.open(ENDPT);
		devLbls.open(DEVLBLS);
		devWav.open(DEVWAV);
		//Trim first line
		trnscr >> strScr; trnwav >> strWav; std::getline(trnlbls,strLbls); trnEndpt >> strEndpt;
		std::getline(devLbls,strDevLbls); devWav >> strDevWav;
		//Load training data
		int i = 0;
		totalFrames = 0;
		for (i = 0;trnscr >> strScr && trnwav >> strWav &&
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
					totalFrames += 1;
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
					totalFrames += 1;
				}
				
				int start, end;
				trnEndpt >> start;
				trnEndpt >> end;
				//push back to existing vector
				trnPair.find(strScr)->second.push_back(std::make_pair(strWav,
												std::make_pair(fenem, std::make_pair(start, end))));
			}
		}
		std::cout << "Load " << i << " lines of training data, with total " << totalFrames <<
		" frames. " << std::endl;
		//Load dev data
		while (std::getline(devLbls,strDevLbls) && devWav >> strDevWav) {
			StringVec fenem;
			
			size_t pos = 0;
			std::string token;
			for (int j = 0; (pos = strDevLbls.find(delimiter)) != std::string::npos; j++) {
				token = strDevLbls.substr(0, pos);
				fenem.push_back(token);
				strDevLbls.erase(0, pos + delimiter.length());
			}
			
			devLabelVec.push_back(fenem);
			devWavVec.push_back(strDevWav);
		}
		std::cout << "Load " << devLabelVec.size() << " lines of dev data" << std::endl;
		
		//Normal mode
		if (!_isContrast) {
			//Set train pair to be whole set
			cstTrnPair = trnPair;
		}
		//Contrast system
		else{
			std::cout << "Using contrastive setting =============" << std::endl;
			std::cout << "Shuffle data into 2 parts..." << std::endl;
			std::set<int> keyList;
			for (TMap::iterator it = trnPair.begin(); it != trnPair.end(); it++) {
				keyList.clear();
				std::string key = it->first;
				std::vector<TPair> pair = it->second;
				int range = (int)pair.size();
				int trainLen = range * 4 / 5;
				std::vector<TPair> insertPair;
				while ((int)insertPair.size() < trainLen) {
					//random a number not in keyList set
					int loc = rand() % range;
					while (keyList.find(loc) != keyList.end()) {
						loc = rand() % range;
					}
					keyList.insert(loc);
					//Add this vector into cstTrnpair
					if (cstTrnPair.find(key) == cstTrnPair.end()) {
						insertPair.push_back(pair[loc]);
					}
				}
				//Insert train pair
				cstTrnPair.insert(std::make_pair(key, insertPair));
				
				//Insert test pair, using left loc's
				insertPair.clear();
				for (int i = 0; i < range; i++) {
					if (keyList.find(i) == keyList.end()) {
						insertPair.push_back(pair[i]);
					}
				}
				cstTstPair.insert(std::make_pair(key, insertPair));
			}
		}
	}
	
public:
	//alpha, beta for trellis
	std::vector<RowVec> _alpha;
	std::deque<ReRowVec> _beta;
	RowVec _coef;
	//total states for a trellis
	float sumStates;
	//Loglikelihood
	std::vector<float> _logProb;
	
	Fenonic(bool isContrast) {
		HIdxMap.clear();
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
		_isContrast = isContrast;
		ConstructData();
	}
	
	void Train() {
		_logProb.clear();
		int insCount = 0;
		for (TMap::iterator it = cstTrnPair.begin(); it != cstTrnPair.end(); it++) {
			curBaseform = _baseform.find(it->first)->second;
			//For each word training instance
			for (std::vector<TPair>::iterator ins = it->second.begin(); ins != it->second.end(); ins++) {
				insCount++;
				if (insCount % 50 == 0) {
					std::cout << "." << std::flush;
				}
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
				//Uniform start probability
				for (RowVec::iterator a0 = alpha0.begin(); a0 != alpha0.end(); a0++) {
					*a0 /= sumStates;
				}
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
			logProb += logf(sum);
			_coef.push_back(sum);
			predAlpha = alphaVec;
			_alpha.push_back(alphaVec);
		}
		_logProb.push_back(logProb);
		//Initialize beta with 1/coefficient
		ReRowVec successorBeta;
		RowVec lastAlpha = _alpha.back();
		float coef = _coef.back();
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
	
	//Given a sequence of observation, find the most likely word
	//Return the most likely word
	std::string findMostLikelyWord(StringVec obs, std::string wav) {
		StringVec cur;
		//record each word and its prob with pair
		std::vector<float> sum;
		std::string maxWord;
		float maxProb = -INFINITY;
		for (Baseform::iterator it = _baseform.begin(); it != _baseform.end(); it++) {
			cur = it->second;
			//log prob
			float prob = 0.0;
			RowVec predAlpha;
			//Reset sum states for each instance
			sumStates = 0.0;
			for (StringVec::iterator fe = cur.begin(); fe != cur.end(); fe++) {
				for (int i = 0; i < fenoMap.find(*fe)->second._numOfStates; i++) {
					predAlpha.push_back(1.0);
					sumStates += 1.0;
				}
			}
			//Uniform start probability
			for (RowVec::iterator a0 = predAlpha.begin(); a0 != predAlpha.end(); a0++) {
				*a0 /= sumStates;
			}
			//Start compute forward
			for (StringVec::iterator it = obs.begin(); it != obs.end(); it++) {
				RowVec alphaVec;
				//for baseform trellis, compute alpha
				int feLoc = 0;
				float sum = 0.0;
				for (StringVec::iterator fe = cur.begin(); fe != cur.end(); fe++) {
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
				predAlpha = alphaVec;
				//Rescale
				prob += logf(sum);
			}
			if (prob > maxProb) {
				maxProb = prob;
				maxWord = it->first;
			}
			//save all prob in the sum, and
			sum.push_back(prob);
		}
		
		//Output only when not in contrast mode
		if (!_isContrast) {
			float sumP = 0;
			//Compute confidence level through exp domain, set throw away threshold to be 20
			for (std::vector<float>::iterator p = sum.begin(); p != sum.end(); p++) {
				if (*p - maxProb > -20) {
					sumP += exp(*p - maxProb);
				}
			}
			maxProb = 1.0/sumP;
			std::cout << "wav file: " << wav << ", predict word : " << maxWord << " with confidence " << maxProb << std::endl;
		}
		
		return maxWord;
	}
	
	//For each test label, find the maximum
	void Predict() {
		std::cout << "Start prediciting outputs on test data..." << std::endl;
		int i = 0;
		for (std::vector<StringVec>::iterator sv = devLabelVec.begin(); sv != devLabelVec.end(); sv++) {
			findMostLikelyWord(*sv, devWavVec[i]);
			i++;
		}
	}
	
	//Compute accuracy, used in contrast mode
	float ComputeAcc() {
		float acc = 0.0;
		float sum = 0.0;
		//For each word in
		std::cout << std::endl;
		for (TMap::iterator it = cstTstPair.begin(); it != cstTstPair.end(); it++) {
			for (std::vector<TPair>::iterator ins = it->second.begin();
				 ins != it->second.end(); ins++) {
				std::string result = findMostLikelyWord(ins->second.first, "");
				if (result == it->first) {
					acc++;
				}
				sum++;
				if (int(sum) % 10 == 0) {
					//report progress
					std::cout << "Right prediction : " << acc << " out of total : " << sum << std::endl;
				}
			}
		}
		std::cout << "Right prediction : " << acc << " out of total : " << sum << std::endl;
		std::cout << "Accuracy : " << acc/sum << std::endl;
		return acc/sum;
	}
	
	void PrintLogLikelihood() {
		float sumP = 0.0;
		for (std::vector<float>::iterator log = _logProb.begin(); log != _logProb.end(); log++) {
			sumP += *log;
		}
		sumP /= totalFrames;
		std::cout << std::endl << "Log-prob : " << sumP << std::endl;
	}
};

#endif
