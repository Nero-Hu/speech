package edu.jhu.cs.hthu;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Random;
import java.util.Set;

import sun.java2d.xr.MutableInteger;

/**
 * Build up a decision tree using chou's algorithm
 * 
 * Should consider a better structure on merging BitEncoding tree
 * 
 * @author Haitang Hu
 *
 */
public class ChouTree {
	public class TreeNode {
		// Record the path, from the very first character
		// 1 for left, 0 for right
		public String path;
		// denote the set of all possible histories
		public Set<String> historySet;
		public Map<String, Map<String, MutableInteger>> gram;
		public TreeNode parent;
		public TreeNode left;
		public TreeNode right;
		// denote the type of this node
		public boolean isTerminal;
		// treeNode word frequency
		public Map<String, Double> probMap;
		public Map<String, Double> freqMap;
		// Count of all histories
		public double setCount;

		public double giniIndex;

		// Keep track of left and right set
		public int askedWord;
		public Set<String> leftSet;
		public Set<String> rightSet;

		/**
		 * Construct root and its prob, freq Map
		 * 
		 * @param histories
		 * @param ngram
		 */
		public TreeNode(Map<String, Map<String, MutableInteger>> ngram,
				int dataSize) {
			this.historySet = new HashSet<String>();
			this.gram = new HashMap<String, Map<String, MutableInteger>>();
			this.historySet.addAll(ngram.keySet());
			this.gram = ngram;
			this.isTerminal = false;

			this.path = "";

			this.askedWord = -1;

			this.probMap = new HashMap<String, Double>();
			this.freqMap = new HashMap<String, Double>();

			this.setCount = C(this.historySet, ngram);
			// Construct root frequency
			for (int i = 0; i < AggCluster.LETTERS.length; i++) {
				String word = Character.toString(AggCluster.LETTERS[i]);
				double wordFreq = F(word, this.historySet, ngram);
				freqMap.put(word, wordFreq / this.setCount);
				probMap.put(word, wordFreq / this.setCount);
			}

			this.giniIndex = giniIndex(this);
		}

		/**
		 * Construct node, given parent, so that we could traverse and smooth
		 * 
		 * @param histories
		 * @param Parent
		 * @param ngram
		 */
		public TreeNode(TreeNode parent,
				Map<String, Map<String, MutableInteger>> ngram, int dataSize) {
			this.gram = new HashMap<String, Map<String, MutableInteger>>();
			this.historySet = new HashSet<String>();
			this.gram = ngram;
			this.historySet.addAll(ngram.keySet());
			this.isTerminal = false;

			this.parent = parent;
			this.path = parent.path;

			this.askedWord = -1;

			this.probMap = new HashMap<String, Double>();
			this.freqMap = new HashMap<String, Double>();

			this.setCount = C(this.historySet, ngram);
			// First fill frequency map
			for (int i = 0; i < AggCluster.LETTERS.length; i++) {
				String word = Character.toString(AggCluster.LETTERS[i]);
				double wordFreq = F(word, this.historySet, ngram);
				freqMap.put(word, wordFreq / this.setCount);
				probMap.put(word, wordFreq / this.setCount);
			}

			// Fill prob map using traverse
			TreeNode upper = parent;
			while (upper != null) {
				for (int i = 0; i < AggCluster.LETTERS.length; i++) {
					String word = Character.toString(AggCluster.LETTERS[i]);
					double oldProb = probMap.get(word);
					probMap.put(word, prob(oldProb, word, upper));
				}
				// // Sanity check for new smoothed probability
				// double check = 0.0;
				// for (int i = 0; i < AggCluster.LETTERS.length; i++) {
				// String word = coding.get(AggCluster.LETTERS[i]);
				// check += probMap.get(word);
				// }

				// Set it to its parent
				upper = upper.parent;
			}
			// Prevent from empty set
			if (this.historySet.isEmpty()) {
				this.giniIndex = 0.0;
			} else {
				this.giniIndex = giniIndex(this) * setCount / dataSize;
			}

		}

		/**
		 * Calculate new prob iteratively
		 * 
		 * @param oldProb
		 * @param word
		 * @param parent
		 * @return
		 */
		private double prob(double oldProb, String word, TreeNode parent) {
			return gamma(parent.setCount) * parent.freqMap.get(word)
					+ (1 - gamma(parent.setCount)) * oldProb;
		}

		// Inverse frequency to be smooth lambda
		private double gamma(double c) {
			return 1. / c;
		}

		private double giniIndex(TreeNode node) {
			double score = 0.0;
			for (Entry<String, Double> entry : node.probMap.entrySet()) {
				double prob = entry.getValue();
				score += prob * prob;
			}
			return 1.0 - score;
		}
	}

	private List<Character> train;
	private List<Character> test;

	private List<Character> dev;
	private List<Character> held_out;

	private Map<String, Map<String, MutableInteger>> dev4gram;
	private Map<String, Map<String, MutableInteger>> heldout_4gram;

	private TreeNode devRoot;
	private TreeNode held_outRoot;
	
	double threshold;

	public ChouTree(double threshold) {
		train = new ArrayList<Character>();
		test = new ArrayList<Character>();

		dev = new ArrayList<Character>();
		held_out = new ArrayList<Character>();

		dev4gram = new HashMap<String, Map<String, MutableInteger>>();
		heldout_4gram = new HashMap<String, Map<String, MutableInteger>>();

		collectStats();
		
		this.threshold = threshold;
	}

	/**
	 * Collect 4gram stats
	 */
	private void collectStats() {
		InputStream textA = this.getClass().getClassLoader()
				.getResourceAsStream("textA.txt");
		InputStream textB = this.getClass().getClassLoader()
				.getResourceAsStream("textB.txt");
		BufferedReader brA = new BufferedReader(new InputStreamReader(textA));
		BufferedReader brB = new BufferedReader(new InputStreamReader(textB));
		// Read file
		try {
			int r;
			// read training set
			while ((r = brA.read()) != -1) {
				char ch = (char) r;
				train.add(ch);
			}

			// read test set
			while ((r = brB.read()) != -1) {
				char ch = (char) r;
				test.add(ch);
			}

		} catch (IOException e) {
			e.printStackTrace();
		}

		int threshold = train.size() * 4 / 5;
		for (int i = 0; i < threshold; i++) {
			if (i >= 3) {
				String cur = train.get(i).toString();
				String history = String.valueOf(train.get(i - 3))
						.concat(String.valueOf(train.get(i - 2)))
						.concat(String.valueOf(train.get(i - 1)));
				if (!dev4gram.containsKey(history)) {
					dev4gram.put(history, new HashMap<String, MutableInteger>());
				}
				MutableInteger InitValue = new MutableInteger(1);
				MutableInteger OldValue = dev4gram.get(history).put(cur,
						InitValue);

				if (OldValue != null) {
					InitValue.setValue(OldValue.getValue() + 1);
				}
			}
			dev.add(train.get(i));
		}
		for (int i = threshold; i < train.size(); i++) {
			if (i >= threshold + 3) {
				String cur = train.get(i).toString();
				String history = String.valueOf(train.get(i - 3))
						.concat(String.valueOf(train.get(i - 2)))
						.concat(String.valueOf(train.get(i - 1)));
				if (!heldout_4gram.containsKey(history)) {
					heldout_4gram.put(history,
							new HashMap<String, MutableInteger>());
				}
				MutableInteger InitValue = new MutableInteger(1);
				MutableInteger OldValue = heldout_4gram.get(history).put(cur,
						InitValue);

				if (OldValue != null) {
					InitValue.setValue(OldValue.getValue() + 1);
				}
			}
			held_out.add(train.get(i));
		}
	}

	private double F(String word, Set<String> histories,
			Map<String, Map<String, MutableInteger>> ngram) {
		double count = 0.0;

		Iterator<String> it = histories.iterator();
		while (it.hasNext()) {
			String his = it.next();
			if (ngram.get(his) != null && ngram.get(his).containsKey(word)) {
				count += ngram.get(his).get(word).getValue();
			}
		}

		return count;
	}

	private double C(Set<String> histories,
			Map<String, Map<String, MutableInteger>> ngram) {
		double count = 0.0;

		Iterator<String> it = histories.iterator();
		while (it.hasNext()) {
			String his = it.next();
			// Add all counts
			for (Entry<String, MutableInteger> entry : ngram.get(his)
					.entrySet()) {
				count += entry.getValue().getValue();
			}
		}

		return count;
	}

	/**
	 * Find all non-terminal frontiers, if all terminal return null
	 * 
	 * @param frontier
	 * @return
	 */
	private List<TreeNode> findNonTermFrontier(List<TreeNode> frontier) {
		List<TreeNode> res = new ArrayList<TreeNode>();
		Iterator<TreeNode> it = frontier.iterator();
		while (it.hasNext()) {
			TreeNode node = it.next();
			if (!node.isTerminal)
				res.add(node);
		}

		return res;
	}

	/**
	 * Return the best separate set
	 * 
	 * @param node
	 * @param askedWord
	 * @return
	 */
	private List<Set<String>> findBestPartition(TreeNode node, int askedWord) {
		List<Set<String>> res = new ArrayList<Set<String>>();

		// Random 2 seperate sets
		Set<String> a = new HashSet<String>();
		Set<String> a_comp = new HashSet<String>();
		Set<String> a_old = new HashSet<String>();
		Set<String> a_comp_old = new HashSet<String>();
		Random rd = new Random();

		// First build equivalent classes, and random assign them into 2 class
		Map<String, Set<String>> equivClass = new HashMap<String, Set<String>>();
		Iterator<String> it = node.historySet.iterator();
		while (it.hasNext()) {
			String his = it.next();
			String key = his.substring(askedWord, askedWord + 1);

			if (!equivClass.containsKey(key)) {
				equivClass.put(key, new HashSet<String>());
				if (rd.nextBoolean())
					a.add(key);
				else
					a_comp.add(key);
			}
			equivClass.get(key).add(his);
		}

		// Chou's algorithm to find the best a and a_comp
		while (!a.equals(a_old) || !a_comp.equals(a_comp_old)) {
			// remember initial status
			a_old = a;
			a_comp_old = a_comp;

			// Construct history set, calculate p(w|A) and p(w|A_comp)
			Set<String> a_set = new HashSet<String>();
			Set<String> a_set_comp = new HashSet<String>();
			// Calculate the word count in clusters
			Map<String, Double> f_wa = new HashMap<String, Double>();
			Map<String, Double> f_wa_comp = new HashMap<String, Double>();

			// Construct set
			Iterator<String> it_set_a = a.iterator();
			while (it_set_a.hasNext()) {
				a_set.addAll(equivClass.get(it_set_a.next()));
			}

			Iterator<String> it_set_a_comp = a_comp.iterator();
			while (it_set_a_comp.hasNext()) {
				a_set_comp.addAll(equivClass.get(it_set_a_comp.next()));
			}
			double f_beta_a = C(a_set, node.gram);
			double f_beta_comp_a = C(a_set_comp, node.gram);

			// Put f_wa map and f_wa_comp map for relative
			for (String word : equivClass.keySet()) {
				f_wa.put(word, F(word, a_set, node.gram) / f_beta_a);
				f_wa_comp.put(word, F(word, a_set_comp, node.gram)
						/ f_beta_comp_a);
			}

			Set<String> new_a = new HashSet<String>();
			Set<String> new_a_comp = new HashSet<String>();
			// For each beta, re-assign its new set
			for (Entry<String, Set<String>> entry : equivClass.entrySet()) {
				double a_sum = 0.0;
				double a_comp_sum = 0.0;

				// For each word
				for (String key : equivClass.keySet()) {
					double f_wb = F(key, entry.getValue(), node.gram)
							/ C(entry.getValue(), node.gram);
					a_sum += Math.pow(f_wb - f_wa.get(key), 2);
					a_comp_sum += Math.pow(f_wb - f_wa_comp.get(key), 2);
				}

				if (a_sum <= a_comp_sum)
					new_a.add(entry.getKey());
				else
					new_a_comp.add(entry.getKey());
			}
			// Update a and compliment set
			a = new_a;
			a_comp = new_a_comp;
		}

		res.add(a);
		res.add(a_comp);

		return res;
	}

	private double growHeldoutTree(String path, int askedWord,
			Set<String> leftSet, Set<String> rightSet) {
		TreeNode res = this.held_outRoot;
		for (int i = 0; i < path.length(); i++) {
			if (Character.digit(path.charAt(i), 10) == 1)
				res = res.left;
			else
				res = res.right;
		}
		// Construct ngram given asked location and history set in history
		Map<String, Map<String, MutableInteger>> lGram = new HashMap<String, Map<String, MutableInteger>>();
		Map<String, Map<String, MutableInteger>> rGram = new HashMap<String, Map<String, MutableInteger>>();
		for (Entry<String, Map<String, MutableInteger>> entry : res.gram
				.entrySet()) {
			String key = entry.getKey();
			if (leftSet.contains(Character.toString(key.charAt(askedWord))))
				lGram.put(key, entry.getValue());
			else if (rightSet.contains(Character.toString(key.charAt(askedWord))))
				rGram.put(key, entry.getValue());
			//unseen history
			else{
				if(leftSet.size() >= rightSet.size())
					lGram.put(key, entry.getValue());
				else
					rGram.put(key, entry.getValue());
			}
		}

		TreeNode left = new TreeNode(res, lGram, dev.size());
		TreeNode right = new TreeNode(res, rGram, dev.size());

		// set relationship
		res.left = left;
		res.right = right;

		return res.giniIndex - left.giniIndex - right.giniIndex;
	}

	private void growDevTree(String path, int askedWord, Set<String> leftSet,
			Set<String> rightSet) {
		TreeNode res = this.devRoot;
		for (int i = 0; i < path.length(); i++) {
			if (Character.digit(path.charAt(i), 10) == 1)
				res = res.left;
			else
				res = res.right;
		}
		// Construct ngram given asked location and history set in history
		Map<String, Map<String, MutableInteger>> lGram = new HashMap<String, Map<String, MutableInteger>>();
		Map<String, Map<String, MutableInteger>> rGram = new HashMap<String, Map<String, MutableInteger>>();
		for (Entry<String, Map<String, MutableInteger>> entry : res.gram
				.entrySet()) {
			String key = entry.getKey();
			if (leftSet.contains(Character.toString(key.charAt(askedWord))))
				lGram.put(key, entry.getValue());
			else
				rGram.put(key, entry.getValue());
		}

		TreeNode left = new TreeNode(res, lGram, dev.size());
		TreeNode right = new TreeNode(res, rGram, dev.size());
		
		res.askedWord = askedWord;
		
		res.leftSet = leftSet;
		res.rightSet = rightSet;

		// concat path
		left.path = left.path.concat("1");
		right.path = right.path.concat("0");
		// set relationship
		res.left = left;
		res.right = right;
	}

	public void buildTree() {
		// Record all the frontiers
		List<TreeNode> frontier = new ArrayList<TreeNode>();
		this.devRoot = new TreeNode(dev4gram, dev.size());
		this.held_outRoot = new TreeNode(heldout_4gram, held_out.size());
		frontier.add(devRoot);
		// Until all frontier is terminal
		List<TreeNode> nonTermFron = findNonTermFrontier(frontier);
		while (!nonTermFron.isEmpty()) {
			double maxReduce = -1.0;
			TreeNode maxNode = null;
			int askedWord = -1;
			Set<String> bestLeft = new HashSet<String>();
			Set<String> bestRight = new HashSet<String>();
			// for all non-termial frontier, ask question and find the best move
			Iterator<TreeNode> it = nonTermFron.iterator();
			while (it.hasNext()) {
				TreeNode cur = it.next();
				// Partition using chou's method based on gini index
				for (int i = 0; i < 3; i++) {
					List<Set<String>> cand = findBestPartition(cur, i);
					Set<String> leftSet = cand.get(0);
					Set<String> rightSet = cand.get(1);

					// Construct ngram given asked location and history set in
					// history
					Map<String, Map<String, MutableInteger>> lGram = new HashMap<String, Map<String, MutableInteger>>();
					Map<String, Map<String, MutableInteger>> rGram = new HashMap<String, Map<String, MutableInteger>>();
					for (Entry<String, Map<String, MutableInteger>> entry : cur.gram
							.entrySet()) {
						String key = entry.getKey();
						if (leftSet.contains(Character.toString(key.charAt(i))))
							lGram.put(key, entry.getValue());
						else
							rGram.put(key, entry.getValue());
					}

					TreeNode left = new TreeNode(cur, lGram, dev.size());
					TreeNode right = new TreeNode(cur, rGram, dev.size());

					if (cur.giniIndex - left.giniIndex - right.giniIndex > maxReduce) {
						maxReduce = cur.giniIndex - left.giniIndex
								- right.giniIndex;
						maxNode = cur;
						askedWord = i;
						bestLeft = leftSet;
						bestRight = rightSet;
					}
				}
			}
			// // Compute entropy reduction on held-out set
			double heldGiniReduce = growHeldoutTree(maxNode.path, askedWord,
					bestLeft, bestRight);
			if (heldGiniReduce < threshold) {
				setNodeTerminal(maxNode.path);
			} else {
				// Grow dev tree only when it is non-terminal
				growDevTree(maxNode.path, askedWord, bestLeft, bestRight);
				// Add the two child back to frontier
				frontier.add(getChild(maxNode.path, true));
				frontier.add(getChild(maxNode.path, false));
			}

			// Update frontier list
			// and remove the parent
			frontier.remove(getNode(maxNode.path));

			// update non-terminal frontier list
			nonTermFron = findNonTermFrontier(frontier);

//			System.out.printf("%f, %f, %d\n", maxReduce, heldGiniReduce, askedWord);
		}
	}

	private void setNodeTerminal(String path) {
		TreeNode res = this.devRoot;
		for (int i = 0; i < path.length(); i++) {
			if (Character.digit(path.charAt(i), 10) == 1)
				res = res.left;
			else
				res = res.right;
		}
		res.isTerminal = true;
	}

	private TreeNode getChild(String path, boolean left) {
		TreeNode res = this.devRoot;
		for (int i = 0; i < path.length(); i++) {
			if (Character.digit(path.charAt(i), 10) == 1)
				res = res.left;
			else
				res = res.right;
		}
		return left ? res.left : res.right;
	}

	private TreeNode getNode(String path) {
		TreeNode res = this.devRoot;
		for (int i = 0; i < path.length(); i++) {
			if (Character.digit(path.charAt(i), 10) == 1)
				res = res.left;
			else
				res = res.right;
		}
		return res;
	}

	// Compute perplexity on test data
	public void perplex() {
		double perplex = 0.0;
		for (int i = 3; i < test.size(); i++) {
			String cur = Character.toString(test.get(i));
			// concat bit vector for history
			String history = String.valueOf(test.get(i - 3))
					.concat(String.valueOf(test.get(i - 2)))
					.concat(String.valueOf(test.get(i - 1)));
			TreeNode leaf = getEquivLeaf(history);
			double prob = leaf.probMap.get(cur);
			perplex += AggCluster.log2(prob);
		}
		perplex = Math.pow(2, -perplex / (test.size() - 3.));
		System.out.printf("Perplexity on test data is %f .\n", perplex);
	}

	/**
	 * Get Equivalence class leaf for any history
	 * 
	 * @param history
	 * @return
	 */
	private TreeNode getEquivLeaf(String history) {
		TreeNode res = this.devRoot;
		// Only ends when is leaf
		while (res.left != null || res.right != null) {
			String answer = Character.toString(history.charAt(res.askedWord));
			if (res.leftSet.contains(answer))
				res = res.left;
			else if(res.rightSet.contains(answer))
				res = res.right;
			else{
				if(res.leftSet.size() >= res.rightSet.size())
					res = res.left;
				else
					res = res.right;
			}
		}
		return res;
	}
}
