//
//  hmm.h
//  HMM
//
//  Created by NeroHu on 3/5/15.
//  Copyright (c) 2015 hthu. All rights reserved.
//

#ifndef HMM_hmm_h
#define HMM_hmm_h

#include <vector>
#include <map>
#include <ctime>

static const int FENO_SIZE = 257;
static const std::string SIL = "<sil>";
static std::map<std::string, int> HIdxMap;

//Row float vector
typedef std::vector<float> RowVec;
//Row vector has better performance that can loop from bottom
typedef std::deque<float> ReRowVec;
typedef std::vector<std::string> StringVec;

class HMM {
	
public:
	int _numOfStates;
	std::string _name;
	//2d array for transition probability
	float **_pProb, **_qProb, **_pCount, **_qCount;
	//3d array for emission probability
	float ***_eProb, ***_eCount;
	
	HMM() { }
	
	HMM(std::string name, int idx) {
		_name = name;
		
		//For sil HMM
		if (name == SIL) {
			_numOfStates = 7;
			//Construct SIL model
			//Initialize transition 2d array
			_pProb = new float*[_numOfStates];
			_pCount =new float*[_numOfStates];
			for (int i = 0; i<_numOfStates; i++) {
				_pProb[i] = new float[_numOfStates];
				_pCount[i] = new float[_numOfStates];
				for (int j = 0; j < _numOfStates; j++) {
					_pProb[i][j] = 0.0;
					_pCount[i][j] = 0.0;
				}
			}
			//Initialize null transition 2d array
			_qProb = new float*[_numOfStates];
			_qCount = new float*[_numOfStates];
			for (int i = 0; i<_numOfStates; i++) {
				_qProb[i] = new float[_numOfStates];
				_qCount[i] = new float[_numOfStates];
				for (int j = 0; j < _numOfStates; j++) {
					_qProb[i][j] = 0.0;
					_qCount[i][j] = 0.0;
				}
			}
			
			//Initialize emission 3d array
			_eProb = new float**[_numOfStates];
			_eCount = new float**[_numOfStates];
			for (int i = 0; i < _numOfStates; i++) {
				_eProb[i] = new float*[_numOfStates];
				_eCount[i] = new float*[_numOfStates];
				for (int j = 0; j < _numOfStates; j++) {
					_eProb[i][j] = new float[FENO_SIZE-1];
					_eCount[i][j] = new float[FENO_SIZE-1];
					for (int k = 0; k < FENO_SIZE-1; k++) {
						_eProb[i][j][k] = 1.0/256.0;
						_eCount[i][j][k] = 0.0;
					}
				}
			}
			
			//Set transition prob
			_pProb[0][1] = 0.5;
			_pProb[0][3] = 0.5;
			_pProb[1][1] = 0.5;
			_pProb[1][2] = 0.5;
			_pProb[2][2] = 0.5;
			_pProb[2][6] = 0.5;
			_pProb[3][4] = 0.5;
			_pProb[4][5] = 0.5;
			_pProb[5][6] = 0.5;
			
			//Set null prob
			_qProb[3][6] = 0.5;
			_qProb[4][6] = 0.5;
			_qProb[5][6] = 0.5;
		}
		//For regular baseform
		else{
			_numOfStates = 2;
			
			//Initialize transition 2d array
			_pProb = new float*[_numOfStates];
			_pCount = new float*[_numOfStates];
			for (int i = 0; i<_numOfStates; i++) {
				_pProb[i] = new float[_numOfStates];
				_pCount[i] = new float[_numOfStates];
				for (int j = 0; j < _numOfStates; j++) {
					_pProb[i][j] = 0.0;
					_pCount[i][j] = 0.0;
				}
			}
			
			//Initialize null transition 2d array
			_qProb = new float*[_numOfStates];
			_qCount = new float*[_numOfStates];
			for (int i = 0; i<_numOfStates; i++) {
				_qProb[i] = new float[_numOfStates];
				_qCount[i] = new float[_numOfStates];
				for (int j = 0; j < _numOfStates; j++) {
					_qProb[i][j] = 0.0;
					_qCount[i][j] = 0.0;
				}
			}
			
			//Initialize emission 3d array
			_eProb = new float**[_numOfStates];
			_eCount = new float**[_numOfStates];
			for (int i = 0; i < _numOfStates; i++) {
				_eProb[i] = new float*[_numOfStates];
				_eCount[i] = new float*[_numOfStates];
				for (int j = 0; j < _numOfStates; j++) {
					_eProb[i][j] = new float[FENO_SIZE-1];
					_eCount[i][j] = new float[FENO_SIZE-1];
					for (int k = 0; k < FENO_SIZE-1; k++) {
						//Initialize emission prob
						if (k == idx) {
							_eProb[i][j][k] = 0.5;
						}
						else{
							_eProb[i][j][k] = 0.5/255.0;
						}
						_eCount[i][j][k] = 0.0;
					}
				}
			}
			_pProb[0][0] = 0.1;
			_pProb[0][1] = 0.8;
			_qProb[0][1] = 0.1;
		}
	}
	
	// Arc based forward, return sum of alpha for normalization
	void ComputeForward(RowVec predAlpha, int startLoc, RowVec *alpha, std::string obs) {
		//Emit symbol index
		int idx = HIdxMap.find(obs)->second;
		float al = 0.0;
		//Carry the null transition from former element
		if (!alpha->empty()) {
			al = alpha->back();
		}
		//First update non-null arc, j is source state, i is target state
		for (int i = 0; i < _numOfStates; i++) {
			for (int j = 0; j < _numOfStates; j++) {
				//State is 1 indexed
				if (_pProb[j][i] != 0) {
					al += predAlpha[startLoc+j] * _pProb[j][i] * _eProb[j][i][idx];
				}
				//Only last states has null transition, so we can safely write this
				//Note at this time, the last number has not been pushed into alpha
				if (_qProb[j][i] != 0) {
					al += alpha->at(startLoc + j) * _qProb[j][i];
				}
			}
			alpha->push_back(al);
			//Set al back to 0
			al = 0.0;
		}
	}
	
	/* Arc based backward
	 Notice that the last states will always have 0 for its beta
	 Also, count the arcs at the same time
	*/
	void ComputeBackwardAndColCounts(RowVec *alpha,float coef, ReRowVec successorBeta, int endLoc, ReRowVec *beta, std::string obs) {
		//Emit symbol index
		int idx = HIdxMap.find(obs)->second;
		//beta that carried from successor
		float botBeta = 0.0;
		//First carry beta from successor beta
		if (!beta->empty()) {
			botBeta = beta->front();
		}
		//0 if the last stage in large trellis
		beta->push_front(botBeta);
		
		float be = 0.0;
		//No need to care about the last states, since its not outgoing to any states
		//it's value is simply the carried from last beta
		for (int i = _numOfStates -2 ; i >=0 ; i--) {
			//i is the source state, j is the target state
			for (int j = 0; j < _numOfStates; j++) {
				if (_pProb[i][j] != 0) {
					float b = successorBeta[endLoc - (_numOfStates-j-1)] * _pProb[i][j] *
						_eProb[i][j][idx];
					float c = alpha->at(endLoc - (_numOfStates - i - 1)) * b;
					be += b/coef;
					//Add counts for corresponding emission and transition
					_eCount[i][j][idx] += c;
					_pCount[i][j] += c;
				}
				if (_qProb[i][j] != 0) {
					be += _qProb[i][j] * botBeta;
					/*Note that the null transition always goes to the last state,
					 so this is always calculated at last, so at this time, be equals the beta 
					 of the source state i, so we could use this beta to calculate the counts
					 for null transition in the same stage from i -> j
					 ***Need to multiply the coefficient back
					*/
					_qCount[i][j] += alpha->at(endLoc - (_numOfStates -i-1)) * _qProb[i][j] * botBeta * coef;
				}
			}
			beta->push_front(be);
			be = 0.0;
		}
	}
	
	//Update transition and emission probabilities
	void Update() {
		//Update
		for (int i = 0; i < _numOfStates; i++) {
			float denom = 0.0;
			for (int j = 0; j < _numOfStates; j++) {
				denom += _pCount[i][j] + _qCount[i][j];
				if (_pCount[i][j] != 0) {
					float sumK = 0.0;
					//pcount can be non-accurate, so here compute sum again
					for (int k = 0; k < FENO_SIZE-1; k++) {
						sumK += _eCount[i][j][k];
					}
					for (int k = 0; k < FENO_SIZE-1; k++) {
						_eProb[i][j][k] = _eCount[i][j][k] / sumK;
					}
				}
			}
			//Update prob for state i
			for (int j = 0; j < _numOfStates; j++) {
				if (denom != 0 ) {
					_pProb[i][j] = _pCount[i][j] / denom;
					_qProb[i][j] = _qCount[i][j] / denom;
				}
			}
		}
	}
	
	//Reset arc counts
	void ResetCounts() {
		for (int i = 0; i < _numOfStates; i++) {
			for (int j = 0; j < _numOfStates; j++) {
				_pCount[i][j] = 0;
				_qCount[i][j] = 0;
				for (int k = 0; k < FENO_SIZE-1; k++) {
					_eCount[i][j][k] = 0;
				}
			}
		}
	}
	
	//Simple output form
	void PrintHMM() {
		std::cout << "HMM for : " << _name << std::endl;
		std::cout << "Transition Probability ==== " << std::endl;
		for (int i = 0; i < _numOfStates; i++) {
			for (int j = 0; j< _numOfStates; j++) {
				std::cout << i << " -> " << j <<" " << _pProb[i][j] << " , ";
			}
			std::cout << std::endl;
		}
		std::cout << "Null Transition Probability ==== " << std::endl;
		for (int i = 0; i < _numOfStates; i++) {
			for (int j = 0; j< _numOfStates; j++) {
				std::cout << i << " -> " << j <<" " <<  _qProb[i][j] << " , ";
			}
			std::cout << std::endl;
		}
		std::cout << "Emission Probability ==== " << std::endl;
		for (int i = 0; i < _numOfStates; i++) {
			for (int j = 0; j< _numOfStates; j++) {
				for (int k = 0; k < FENO_SIZE-1; k++) {
					std::cout << i << " , " << j << "," << k << " : " <<  _eProb[i][j][k] << " , ";
					std::cout << HIdxMap.find(_name)->second << std::endl;
				}
				std::cout << std::endl;
			}
			std::cout << std::endl;
		}
	}
	
	void PrintCounts() {
		std::cout << "HMM for : " << _name << std::endl;
		std::cout << "Transition Counts ==== " << std::endl;
		for (int i = 0; i < _numOfStates; i++) {
			for (int j = 0; j< _numOfStates; j++) {
				if (_pCount[i][j] == 0) {
					continue;
				}
				std::cout << i << " -> " << j <<" " << _pCount[i][j] << " , ";
			}
			std::cout << std::endl;
		}
		std::cout << "Null Transition Counts ==== " << std::endl;
		for (int i = 0; i < _numOfStates; i++) {
			for (int j = 0; j< _numOfStates; j++) {
				if (_qCount[i][j] == 0) {
					continue;
				}
				std::cout << i << " -> " << j <<" " <<  _qCount[i][j] << " , ";
			}
			std::cout << std::endl;
		}
		std::cout << "Emission Counts ==== " << std::endl;
		for (int i = 0; i < _numOfStates; i++) {
			for (int j = 0; j< _numOfStates; j++) {
				for (int k = 0; k < FENO_SIZE-1; k++) {
					if (_eCount[i][j][k] == 0) {
						continue;
					}
					std::cout << i << " , " << j << "," << k << " : " <<  _eCount[i][j][k] << " , ";
				}
				std::cout << std::endl;
			}
			std::cout << std::endl;
		}
	}
	
};

#endif
