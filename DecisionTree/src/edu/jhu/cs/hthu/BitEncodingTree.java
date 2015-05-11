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
import java.util.Set;

import sun.java2d.xr.MutableInteger;

/**
 * Bit encoding tree
 * 
 * @author Haitang Hu
 *
 */
public class BitEncodingTree {

	/**
	 * Node of a tree, after ask questions
	 * 
	 * @author nerohu
	 *
	 */
	public class TreeNode {
		// Record the path, from the very first character
		// 1 for left, 0 for right
		public String path;
		// denote the set of all possible histories
		public Set<String> historySet;
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
		// Remember the bit asked, and the child are only allowed to ask the
		// next bit to it
		public int[] allowedBits = new int[3];
		public double entropy;
		// question on this node
		public int askedBit;

		/**
		 * Construct root and its prob, freq Map
		 * 
		 * @param histories
		 * @param ngram
		 */
		public TreeNode(Set<String> histories,
				Map<String, Map<String, MutableInteger>> ngram, int dataSize) {
			this.historySet = new HashSet<String>();
			this.historySet.addAll(histories);
			this.isTerminal = false;

			this.path = "";

			this.probMap = new HashMap<String, Double>();
			this.freqMap = new HashMap<String, Double>();

			this.setCount = C(histories, ngram);
			// Construct root frequency
			for (int i = 0; i < AggCluster.LETTERS.length; i++) {
				String word = coding.get(AggCluster.LETTERS[i]);
				double wordFreq = F(word, histories, ngram);
				freqMap.put(word, wordFreq / this.setCount);
				probMap.put(word, wordFreq / this.setCount);
			}
			// Initialize asked bits to be 0, 9, 18
			for (int i = 0; i < allowedBits.length; i++) {
				allowedBits[i] = i * depth;
			}

			this.entropy = entropy(this) * setCount / (double) dataSize;
		}

		/**
		 * Construct node, given parent, so that we could traverse and smooth
		 * 
		 * @param histories
		 * @param Parent
		 * @param ngram
		 */
		public TreeNode(Set<String> histories, TreeNode parent,
				int[] allowedBits,
				Map<String, Map<String, MutableInteger>> ngram, int dataSize) {
			this.historySet = new HashSet<String>();
			this.historySet.addAll(histories);
			this.isTerminal = false;

			this.parent = parent;
			this.path = parent.path;

			this.allowedBits = allowedBits;

			this.probMap = new HashMap<String, Double>();
			this.freqMap = new HashMap<String, Double>();

			this.setCount = C(histories, ngram);
			// First fill frequency map
			for (int i = 0; i < AggCluster.LETTERS.length; i++) {
				String word = coding.get(AggCluster.LETTERS[i]);
				double wordFreq = F(word, histories, ngram);
				freqMap.put(word, wordFreq / this.setCount);
				probMap.put(word, wordFreq / this.setCount);
			}

			// Fill prob map using traverse
			TreeNode upper = parent;
			while (upper != null) {
				for (int i = 0; i < AggCluster.LETTERS.length; i++) {
					String word = coding.get(AggCluster.LETTERS[i]);
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
			if (histories.isEmpty()) {
				this.entropy = 0.0;
			} else {
				this.entropy = entropy(this) * setCount / (double) dataSize;
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
	}

	private Map<Character, String> coding;
	private int depth;

	private List<Character> train;
	private List<Character> dev;
	private List<Character> held_out;
	// key to be map of 4gram, String for bit vector of one of 27 letters
	private Map<String, Map<String, MutableInteger>> dev4gram;
	private Map<String, Map<String, MutableInteger>> heldout_4gram;

	private List<Character> test;

	private TreeNode devRoot;
	private TreeNode heldoutRoot;

	public BitEncodingTree(Cluster root) {
		depth = 0;
		coding = new HashMap<Character, String>();

		train = new ArrayList<Character>();
		dev = new ArrayList<Character>();
		held_out = new ArrayList<Character>();

		dev4gram = new HashMap<String, Map<String, MutableInteger>>();
		heldout_4gram = new HashMap<String, Map<String, MutableInteger>>();

		test = new ArrayList<Character>();

		getDepth(root, 0);

		// Build encoding from root clustered from Decision Tree
		encode(root, "");

		// Read data, 80% dev, 20% held-out
		collectStats();

		// printEncoding();
	}

	/**
	 * Get depth of a tree
	 * 
	 * @param c
	 * @param level
	 */
	private void getDepth(Cluster c, int level) {
		if (c == null)
			return;
		if (level > depth) {
			depth = level;
		}
		getDepth(c.left, level + 1);
		getDepth(c.right, level + 1);
	}

	/**
	 * Encode each letter to be represented by bit
	 * 
	 * @param c
	 * @param level
	 */
	private void encode(Cluster c, String level) {
		if (c == null)
			return;
		// Leaf, then set the encoding for the letter
		if (c.letters.size() == 1) {
			String bit = new String();
			for (int i = 0; i < this.depth; i++) {
				if (i < level.length()) {
					bit = bit.concat(Character.toString(level.charAt(i)));
				}
				// padding zero
				else {
					bit = bit.concat("0");
				}
			}
			coding.put(c.letters.get(0), bit);
		}
		encode(c.left, level.concat("1"));
		encode(c.right, level.concat("0"));
	}

	/**
	 * Prepare training/held-out data set
	 */
	// read text file and construct unigram & bigram statistics
	private void collectStats() {
		// Get input from working directory
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

		// Gather statistics
		// for dev data
		int threshold = train.size() * 4 / 5;
		for (int i = 0; i < threshold; i++) {
			if (i >= 3) {
				String cur = coding.get(train.get(i));
				// concat bit vector for history
				String history = concatBitVector(
						coding.get(train.get(i - 3)),
						concatBitVector(coding.get(train.get(i - 1)),
								coding.get(train.get(i - 2))));
				// if not exist, add key to history map
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
		// for held-out data
		for (int i = threshold; i < train.size(); i++) {
			if (i >= threshold + 3) {
				String cur = coding.get(train.get(i));
				// concat bit vector for history
				String history = concatBitVector(
						coding.get(train.get(i - 3)),
						concatBitVector(coding.get(train.get(i - 1)),
								coding.get(train.get(i - 2))));
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

	private String concatBitVector(String a, String b) {
		return a.concat(b);
	}

	/**
	 * Frequency Count of a set of histories
	 * 
	 * @param histories
	 * @param ngram
	 * @return
	 */
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
	 * Compute word frequency given equivalence classes
	 * 
	 * @param word
	 * @param histories
	 * @param ngram
	 * @return
	 */
	private double F(String word, Set<String> histories,
			Map<String, Map<String, MutableInteger>> ngram) {
		double count = 0.0;

		Iterator<String> it = histories.iterator();
		while (it.hasNext()) {
			String his = it.next();
			if (ngram.get(his).containsKey(word)) {
				count += ngram.get(his).get(word).getValue();
			}
		}

		return count;
	}

	// Inverse frequency to be smooth lambda
	private double gamma(double c) {
		return 1. / c;
	}

	private double entropy(TreeNode node) {
		double score = 0.0;
		for (Entry<String, Double> entry : node.probMap.entrySet()) {
			double prob = entry.getValue();
			score += -prob * AggCluster.log2(prob);
		}
		return score;
	}

	/**
	 * Build up a tree use bit-encoding
	 */
	public void buildTree() {
		double threshold = 0.005;
		// Record all the frontiers
		List<TreeNode> frontier = new ArrayList<TreeNode>();
		// First place all set into root
		this.devRoot = new TreeNode(dev4gram.keySet(), dev4gram, dev.size());
		this.heldoutRoot = new TreeNode(heldout_4gram.keySet(), heldout_4gram,
				held_out.size());

		frontier.add(devRoot);

		// Until all frontier is terminal
		List<TreeNode> nonTermFron = findNonTermFrontier(frontier);
		while (!nonTermFron.isEmpty()) {
			double maxReduce = -1.0;
			TreeNode maxNode = null;
			int askedBit = -1;
			// for all non-termial frontier, ask question and find the best move
			Iterator<TreeNode> it = nonTermFron.iterator();
			while (it.hasNext()) {
				TreeNode cur = it.next();
				// for all allowed questions, seperete into two sets and compute
				// entropy reduction
				for (int i = 0; i < cur.allowedBits.length; i++) {
					int bitLoc = cur.allowedBits[i];
					// Otherwise already explore all questions in one bit
					if (bitLoc < i * this.depth + this.depth) {
						// sep[0] is left, sep[1] is right
						List<Set<String>> sep = seperateSets(bitLoc, cur);
						if (sep != null) {
							TreeNode left = new TreeNode(sep.get(0), cur,
									new int[3], dev4gram, dev.size());
							TreeNode right = new TreeNode(sep.get(1), cur,
									new int[3], dev4gram, dev.size());
							double entropy = left.entropy + right.entropy;
							if (cur.entropy - entropy > maxReduce) {
								maxReduce = cur.entropy - entropy;
								maxNode = cur;
								askedBit = bitLoc;
							}
						}
					}
				}
			}
			// Compute entropy reduction on held-out set
			double heldEntropyReduce = growHeldoutTree(maxNode.path, askedBit);
			if (heldEntropyReduce < threshold) {
				setNodeTerminal(maxNode.path);
			} else {
				// Grow dev tree only when it is non-terminal
				growDevTree(maxNode.path, askedBit);
				// Add the two child back to frontier
				frontier.add(getChild(maxNode.path, true));
				frontier.add(getChild(maxNode.path, false));
			}

			// Update frontier list
			// and remove the parent
			frontier.remove(getNode(maxNode.path));

			// update non-terminal frontier list
			nonTermFron = findNonTermFrontier(frontier);

//			System.out.printf("%s, %d, %f, %f\n", maxNode.path, askedBit,
//					maxReduce, heldEntropyReduce);
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

	/**
	 * given path, find child nodes false to get right child, true to get left
	 * child
	 * 
	 * @param path
	 * @return
	 */
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

	/**
	 * Grow held out tree given grow node in dev tree, and the question asked
	 * return entropy reduced
	 * 
	 * @param path
	 * @param askedBit
	 * @return
	 */
	private double growHeldoutTree(String path, int askedBit) {
		TreeNode res = this.heldoutRoot;
		for (int i = 0; i < path.length(); i++) {
			if (Character.digit(path.charAt(i), 10) == 1)
				res = res.left;
			else
				res = res.right;
		}
		List<Set<String>> sep = seperateSets(askedBit, res);
		TreeNode left = new TreeNode(sep.get(0), res, new int[3],
				heldout_4gram, held_out.size());
		TreeNode right = new TreeNode(sep.get(1), res, new int[3],
				heldout_4gram, held_out.size());
		// concat path
		left.path = left.path.concat("1");
		right.path = right.path.concat("0");
		// set relationship
		res.left = left;
		res.right = right;
		return (res.entropy - left.entropy - right.entropy);
	}

	private void growDevTree(String path, int askedBit) {
		TreeNode res = this.devRoot;
		for (int i = 0; i < path.length(); i++) {
			if (Character.digit(path.charAt(i), 10) == 1)
				res = res.left;
			else
				res = res.right;
		}
		// Set asked bit for the node
		res.askedBit = askedBit;

		List<Set<String>> sep = seperateSets(askedBit, res);
		// Increase the allowed bits for asked bit
		int[] newallowedBits = new int[3];
		for (int i = 0; i < newallowedBits.length; i++) {
			newallowedBits[i] = res.allowedBits[i];
		}
		newallowedBits[askedBit / this.depth]++;

		TreeNode left = new TreeNode(sep.get(0), res, newallowedBits, dev4gram,
				dev.size());
		TreeNode right = new TreeNode(sep.get(1), res, newallowedBits,
				dev4gram, dev.size());
		// concat path
		left.path = left.path.concat("1");
		right.path = right.path.concat("0");
		// set relationship
		res.left = left;
		res.right = right;
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
	 * Seperate sets into 2 branch given asked question bits Left should
	 * contains bits to be 1, right should contains bits to be 0
	 * 
	 * @param bitIndex
	 * @param node
	 * @return
	 */
	private List<Set<String>> seperateSets(int bitIndex, TreeNode node) {

		List<Set<String>> res = new ArrayList<Set<String>>();
		Set<String> left = new HashSet<String>();
		Set<String> right = new HashSet<String>();

		Iterator<String> it = node.historySet.iterator();
		while (it.hasNext()) {
			String his = it.next();
			if (Character.digit(his.charAt(bitIndex), 10) == 1) {
				left.add(his);
			} else
				right.add(his);
		}
		res.add(left);
		res.add(right);

		return res;
	}

	/**
	 * Print encoding
	 */
	public void printEncoding() {
		Iterator<Character> it = coding.keySet().iterator();
		while (it.hasNext()) {
			Character val = it.next();
			System.out.printf("%s : %s\n", val, coding.get(val));
		}
	}

	// Compute perplexity on test data
	public void perplex() {
		double perplex = 0.0;
		for (int i = 3; i < test.size(); i++) {
			String cur = coding.get(test.get(i));
			// concat bit vector for history
			String history = concatBitVector(
					coding.get(test.get(i - 3)),
					concatBitVector(coding.get(test.get(i - 1)),
							coding.get(test.get(i - 2))));
			TreeNode leaf = getEquivLeaf(history);
			double prob = leaf.probMap.get(cur);
			perplex += AggCluster.log2(prob);
		}
		perplex = Math.pow(2, - perplex / (test.size() - 3.));
		System.out.printf("Perplexity on test data is %f .\n", perplex);
	}
	
	/**
	 * Get Equivalence class leaf for any history
	 * @param history
	 * @return
	 */
	private TreeNode getEquivLeaf(String history) {
		TreeNode res = this.devRoot;
		// Only ends when is leaf
		while(res.left != null || res.right != null) {
			int askedBit = res.askedBit;
			int answer = Character.digit(history.charAt(askedBit), 10);
			if (answer == 1)
				res = res.left;
			else
				res = res.right;
		}
		return res;
	}
}
