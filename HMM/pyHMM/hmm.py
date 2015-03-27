#!/usr/bin/env python
import sys
import math
import numpy as np
from collections import defaultdict

class HMM:
	"""A simple implementation for HMM Model.

	numOfStates: int
		number of states in HMM Model

	numOfSymbols: int
		number of observations in HMM Model

	startProb: array, shape ('numOfStates')
		indicate different start probability for each states

	transProb: array, shape ('numOfStates', 'numOfStates')
		transition probability matrix

	emissionProb: array, shape ('numOfStates', 'numOfSymbols')
		emission probability for all state-observation pair

	numOfIter: int
		default iteration times for EM

	convThresh: float
		Convergence threshold for EM

	"""
	def __init__(self, numOfStates=1, numOfSymbols=1, startProb=None, transProb=None, emissionProb=None, numOfIter=50, convThresh=1e-5, isArc=False):
		self.numOfStates = numOfStates
		self.numOfSymbols = numOfSymbols
		self.startProb = startProb
		self.transProb = transProb
		self.emissionProb = emissionProb
		self.numOfIter = numOfIter
		self.convThresh	= convThresh
		self.isArc = isArc
		self.logProb = []
		self.characterProb = defaultdict(list)

	def train(self, obs):
		"""Train HMM Model given observations

		obs: array, shape('length of observations sequence')

		"""
		# For test set
		# heldoutData = open("textB.txt").readline()
		# eva = np.zeros(len(heldoutData))

		# letterMap = defaultdict()
		# letters = 'abcdefghijklmnopqrstuvwxyz '
		# countFreq = defaultdict(int)
		# for alphabet in letters:
		# 	letterMap[alphabet] = len(letterMap)
		# 	countFreq[alphabet] += 1

		# for (n, letter) in enumerate(heldoutData):
		# 	eva[n] = letterMap[letter]

		# numOfStates = 2
		# numOfSymbols = 27
		# h = HMM(numOfStates, numOfSymbols,1)

		it = 0
		self.diff = np.inf
		while it < 2:
		# while it < self.numOfIter and self.diff > self.convThresh:
			it += 1
			print it
			self.em(obs)
		# 	h.startProb = self.startProb
		# 	# Uniform 2 states
		# 	h.transProb = self.transProb
		# 	h.emissionProb = self.emissionProb
		# 	h.forward(eva)

		# # print 'On test data \n===================='
		# print h.logProb

	def forward(self, obs):
		"""Compute alpha using forward propagation

		alpha = array, shape ('numOfStates', 'length of observation sequence')

		Implementation description:

		For arc transition model.
		=================================
		First compute S * S matrix, that specifies joint p(output = y, s = si | s' = sj)

		self.transProb.T                         *          emissionProb[:,obs[n]] 
							s1,	s2, ..., sn    					output = y
					  s1 [ 1->1             ] 					[ y | s1]
					  s2 [ 1->2             ]					[ y | s2]
					  ..                    ]					...
					  sn [ 1->n             ]					[ y | sn]
	
		According to the broadcast rule, we have the transpose of the above result matrix to be
				s1,	  s2,      ...,  sn    	
		 s1 [ y|1->1, y|1->2        y|1->n  ] 
		 s2 [ y|2->1, y|2->2        y|2->n  ]
		 ..                                 ]
		 sn [ y|n->1, y|n->2        y|n->n  ]

						   obs[0],      osb[1], ... ,    obs[n]
		alpha =  state[1]                 ||
				 state[2]                 ||
				 ...                      ||
				 state[S]                 ||
			s1,	  s2,      ...,  sn    	        alpha[:,i].T = 	 														 	
		s1 [ y|1->1, y|1->2        y|1->n  ] [ alpha_i[s1], alpha_i[s2]] ... , alpha_i[sn] ]
		s2 [ y|2->1, y|2->2        y|2->n  ]																    
		..                                 ]																    
		sn [ y|n->1, y|n->2        y|n->n  ]																    

		The dot product is going to be a row vector with size S, where each enrty equals:

		alpha_i[s1] * p[y, i | 1] + alpha_i[s2]] * p[y, i | 2] + ... + alpha_i[sn] * p[y, i | n]

		which is exactly what we want, all the input arc into some state.

		Multiply the emissionProb is trival.

		For state emission model.
		=================================


		"""
		if self.startProb == None:
			print 'Start probability is not set'
			return
		self.alpha = np.zeros((self.numOfStates, len(obs)))
		#Coefficient that normalize each column of alpha
		self.coef = np.zeros(len(obs))

		#If arc transition
		if self.isArc:
			#TODO
			pass
		#state emission
		else:
			self.alpha[:,0] = self.startProb * self.emissionProb[:,obs[0]]
			self.coef[0] =  np.sum(self.alpha[:,0])
			self.alpha[:,0] /= self.coef[0]
			for n in range(1,len(obs)):
				self.alpha[:,n] = np.dot(self.alpha[:,n-1], self.transProb) * self.emissionProb[:,obs[n]]
				self.coef[n] = np.sum(self.alpha[:,n])
				self.alpha[:,n] /= self.coef[n]

		#Record log probability
		self.logProb.append(np.sum(np.log(self.coef))/len(obs))
		print self.alpha
		return self.alpha

	def backword(self, obs):
		"""Compute beta using backward propagation

		beta = array, shape ('numOfStates', 'length of observation sequence')

		"""
		self.beta = np.zeros((self.numOfStates, len(obs)))
		self.beta[:,len(obs)-1] = 1/self.coef[len(obs)-1]

		if self.isArc:
			#TODO
			pass
		#state emission
		else:
			for n in range(1,len(obs)):
				loc = len(obs) - n
				self.beta[:,loc-1] = np.dot(self.transProb, self.emissionProb[:,obs[loc]] * self.beta[:,loc])
				self.beta[:,loc-1] /= self.coef[loc-1]

		return self.beta

	def em(self, obs):
		"""
		EM algorithm for hmm
		
		Save parameter difference into self.diff, to check if converged

		"""
		self.forward(obs)
		self.backword(obs)

		#Collect sigma
		sigma = np.zeros((len(obs)-1, self.numOfStates, self.numOfStates))
		# alpha * beta
		gamma = self.alpha * self.beta

		for t in range(len(obs) - 1):
			#Denominator is all possible transitions between alpha[t] and beta[t+1], emitting obs[t]
			denom = 0.0
			#Compute transition through one state
			for i in range(self.numOfStates):
				for j in range(self.numOfStates):
					sigma[t,i,j] = self.alpha[i][t] * self.transProb[i][j] * self.beta[j][t+1] * self.emissionProb[j][obs[t+1]]
					denom += sigma[t,i,j]
			sigma[t] /= denom
		
		#Collect gamma
		gamma /= np.sum(gamma, axis = 0)

		#Re-estimate parameters
		#For transition probability
		self.oldTransProb = self.transProb
		for state in range(self.numOfStates):
			self.transProb[state,:] = np.sum(sigma,axis=0)[state,:] / np.sum(gamma[state,:])
			#Normalize
			self.transProb[state,:] /= np.sum(self.transProb[state,:])

		#Update date start probability
		self.oldStartProb = self.startProb
		self.startProb = gamma[:,0]

		#For emission probability
		self.oldEmissionProb = self.emissionProb
		self.emissionProb = np.zeros((self.numOfStates, self.numOfSymbols))
		stateDenom = np.sum(gamma, axis=1)
		for t in range(len(obs)):
			for state in range(self.numOfStates):
				self.emissionProb[state][obs[t]] += gamma[state][t] / stateDenom[state]

		self.diff = np.sum(np.sum(abs(self.transProb - self.oldTransProb))) + np.sum(np.sum(abs(self.emissionProb - self.oldEmissionProb))) \
			+ np.sum(abs(self.startProb - self.oldStartProb))

	def viterbi(self, obs):
		"""Viterbi algorithm to find the most likely states sequence

		obs: array, shape('length of observations sequence')

		return array, shape('length of observations sequence')

		Implementation description:
		Example from wiki: https://en.wikipedia.org/wiki/Viterbi_algorithm

		              	 normal,  cold,    dizzy
		emit =  health	[0.5,	   0.4,	    0.1]
		        fever 	[0.1,	   0.3,	    0.6]

		        		 health,  fever
		trans = health  [ 0.7      0.3]
		        fever   [ 0.4      0.6]


		prob = health   [0.3]
		       fever    [0.04]

		Numpy broadcast multiply:
		                          
		transProb = 
		prob * trans.T =  all input arc to health   [ [health->health], [fever->health]
						  all input arc to fever      [health->fever ], [fever->fever ] ]

		Multiply transProb by emission probability using numpy broadcast
		transProb.T * emissionProb[:,obs[t]] = [ [health->health] * [health emit state],[health->fever ] * [health emit state]
  												[fever->health ] * [fever emit state], [fever->fever ] * [fever emit state]]   

  		Result array will be based on ColumnProb, where each column denotes all income arcs that point to the state,
  		e.g: 			  state0     state1
  		        state0     0.1         0.5
  		        state1     0.2         0.4
  		Then, we take the maximum in column 0, to be the best probability for state 0. 
  		And also, we record the state that is corresponding to maximum, record it as a backPoint.
  		We do it for every column in result array.

		"""
		# Initialize path to be a list with length of observation sequence
		path = [[] for seq in range(self.numOfStates)]
		# Base case
		prob = self.startProb * self.emissionProb[:,obs[0]]
		#Start to find path
		for t in range(1, len(obs)):
			probArray = (prob * self.transProb.T).T * self.emissionProb[:,obs[t]]
			#Find maximum for each column, to be the best probability and the backpoint
			prob = probArray.max(axis=0)
			backPoint = probArray.argmax(axis=0)
			[path[state].append(backPoint[state]) for state in range(self.numOfStates)]

		#Get max for the last state, and append it to the path
		lastMaxState = prob.argmax()
		path[lastMaxState].append(lastMaxState)
		return path[lastMaxState]

#Test below
def main(argv):
	numOfStates = 2
	numOfSymbols = 2
	hmm = HMM(numOfStates, numOfSymbols)
	hmm.startProb = np.array([0.85, 0.15])
	hmm.transProb = np.array([[0.3, 0.7], [0.1, 0.9]])
	hmm.emissionProb = np.array([[0.4, 0.6], [0.5, 0.5]])
	obs = np.array([0,1,1,0])
	hmm.train(obs)
	print hmm.transProb
	print hmm.emissionProb
	print hmm.logProb
#Main entry
if __name__ == '__main__':
	main(sys.argv)
