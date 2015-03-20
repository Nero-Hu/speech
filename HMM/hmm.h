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
#include <iostream>
#include <deque>

typedef std::vector<int> ObsVector;
typedef std::vector<float> RowVector;

typedef std::deque<RowVector> Beta;

typedef std::vector<RowVector> Table;

class HMM {

private:
	int _states;
	int _iter;

	void ComputeForward(ObsVector obs) {
        // Clear old values
		_alpha.clear();
		_coef.clear();

		for (int it = 0; it < obs.size(); it++) {
			RowVector alpha;
			float sum = 0.0;

			if( it == 0) {
				for (int i = 0; i < _states; i++) {
					alpha.push_back(_startProb[i] * _emissionProb[i][obs[it]]);
					sum += alpha[i];
				}
				for (int i = 0; i < _states; i++) {
					alpha[i] /= sum;
				}
				_alpha.push_back(alpha);
				_coef.push_back(sum);
				continue;
			}
			for (int i = 0; i < _states; i++) {
				float score = 0.0;
				for (int j = 0; j < _states; j++) {
					score += _alpha[it-1][j] * _transitionProb[j][i] * _emissionProb[i][obs[it]];
				}
				sum += score;
				alpha.push_back(score);
			}
												//Normalize alpha, and save coefficient
			for (int i =0; i < _states; i++) {
				alpha[i] /= sum;
			}
			_coef.push_back(sum);
			_alpha.push_back(alpha);
		}
	}

	void ComputeBackward(ObsVector obs) {
		_beta.clear();
		for (int it = (int)obs.size() - 1 ; it >= 0 ; it--) {
			float coef = _coef[it];
			RowVector beta;
			if( it == (int)obs.size() -1 ) {
				for (int i = 0; i < _states; i++) {
					beta.push_back(1.0/coef);
				}
				_beta.push_front(beta);
				continue;
			}
			for (int i = 0; i < _states; i++) {
				float score = 0.0;
				for (int j = 0; j < _states; j++) {
					score += _beta[0][j] * _transitionProb[i][j] * _emissionProb[j][obs[it+1]]
					/ coef;
				}
				beta.push_back(score);
			}
			_beta.push_front(beta);
		}
	}

	void NormalizeTableByRow(Table *table) {
		for (Table::iterator it=table->begin(); it != table->end(); it++) {
			float sum = 0.0;
			for (RowVector::iterator row = it->begin(); row != it->end(); row++) {
				sum += *row;
			}
			for (RowVector::iterator row = it->begin(); row != it->end(); row++) {
				*row /= sum;
			}
		}
	}



	void doEM(ObsVector obs) {
		ComputeForward(obs);
		ComputeBackward(obs);

		Table gamma;
								//Save sum of state gamma as denominator
		RowVector stateGammaSum;
		stateGammaSum.resize(_states);

		Table sigma;
		sigma.resize(_states);

								//Compute gamma & Compute denominator for sigma
		for (int it = 0; it < (int)obs.size(); it++) {
			RowVector rowGamma;
			float sum = 0.0;
			for (int j = 0; j < _states; j++) {
				float albeta = _alpha[it][j] * _beta[it][j];
				rowGamma.push_back(albeta);
				sum += albeta;
			}
			for (int j = 0; j < _states; j++) {
				rowGamma[j] /= sum;
				stateGammaSum[j] += rowGamma[j];
			}
			gamma.push_back(rowGamma);

			float denom = 0.0;
			Table	sigmaCount;

			if (it < (int)obs.size()-1) {
				for (int i = 0 ; i < _states; i++) {
					RowVector sigmaRow;
					for (int j = 0; j < _states; j++) {
						float sig = _alpha[it][i] * _transitionProb[i][j] *
						_beta[it+1][j] * _emissionProb[j][obs[it+1]];
						denom += sig;
						sigmaRow.push_back(sig);
					}
					sigmaCount.push_back(sigmaRow);
				}

				for (int i = 0 ; i < _states; i++) {
					sigma[i].resize(_states);
					for (int j = 0; j < _states; j++) {
						sigma[i][j] += sigmaCount[i][j] / denom;
					}
				}
			}
		}

								//Re-estimate Transition probability
		for (int i = 0 ; i < _states; i++) {
			for (int j = 0; j < _states; j++) {
				_transitionProb[i][j] = sigma[i][j] / stateGammaSum[i];
			}
		}
		NormalizeTableByRow(&_transitionProb);

								//Update start probability using gamma[0]
		_startProb = gamma[0];

								//Update emission probability
		for (Table::iterator it = _emissionProb.begin(); it != _emissionProb.end(); it++) {
			std::fill(it->begin(), it->end(), 0.0);
		}
		for (int it = 0; it < (int)obs.size(); it++) {
			for (int i = 0; i < _states; i++) {
				_emissionProb[i][obs[it]] += gamma[it][i] / stateGammaSum[i];
			}
		}
	}
	
	void printTable(Table t) {
		for (Table::iterator it = t.begin() ; it != t.end(); ++it){
			for (RowVector::iterator r = it->begin(); r != it->end(); ++r) {
				std::cout << *r << ", " ;
			}
			std::cout << std::endl;
		}
	}

public:
	Table _alpha, _transitionProb, _emissionProb;
	RowVector _coef, _startProb, _logProb;
	Beta _beta;

	HMM() { }

	HMM(RowVector startProb, Table transitionProb, Table emissionProb) {
		_startProb = startProb;
		_transitionProb = transitionProb;
		_emissionProb = emissionProb;

		_states = (int)_transitionProb.size();
		_iter = 5;
	}

	void setIter(int iter) {
		_iter = iter;
	}

	void Train(ObsVector obs) {
		_alpha.reserve(obs.size());
		for (int it = 0; it < _iter; it++ ) {
			std::cout.flush();
			if ( it%20 == 0) {
				std::cout << '.' ;
			}
			doEM(obs);
		}
		std::cout << std::endl;
	}

	void printResult() {
		std::cout << "Transiton Probability=======" << std::endl;
		printTable(_transitionProb);
		std::cout << "Emission Probability=======" << std::endl;
		printTable(_emissionProb);
	}

};

#endif
