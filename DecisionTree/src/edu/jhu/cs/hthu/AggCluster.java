package edu.jhu.cs.hthu;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;

import sun.java2d.xr.MutableInteger;

public class AggCluster {

	public static char[] LETTERS = "abcdefghijklmnopqrstuvwxyz ".toCharArray();

	private List<Character> train;
	private List<Character> test;

	private Map<Character, MutableInteger> trainUnigramStats;
	private Map<Character, MutableInteger> testUnigramStats;

	private Map<Character, Bigram> trainBigramStats;
	private Map<Character, Bigram> testBigramStats;

	private List<Cluster> clusters;

	private Cluster root;

	public AggCluster() {
		// Initialize variables
		train = new ArrayList<Character>();
		test = new ArrayList<Character>();

		trainUnigramStats = new HashMap<Character, MutableInteger>();
		testUnigramStats = new HashMap<Character, MutableInteger>();

		// Character and it bigram map
		trainBigramStats = new HashMap<Character, Bigram>();
		testBigramStats = new HashMap<Character, Bigram>();

		clusters = new ArrayList<Cluster>();

		for (int i = 0; i < LETTERS.length; i++) {
			trainBigramStats.put(LETTERS[i], new Bigram(LETTERS[i]));
			testBigramStats.put(LETTERS[i], new Bigram(LETTERS[i]));
			// Construct base case clusters
			clusters.add(new Cluster(LETTERS[i]));
		}

		// Collect unigram & bigram statistics
		collectStats();
	}

	// read text file and construct unigram & bigram statistics
	private void collectStats() {
		// Get input from working directory
		InputStream textA = this.getClass().getClassLoader()
				.getResourceAsStream("textA.txt");
		InputStream textB = this.getClass().getClassLoader()
				.getResourceAsStream("textB.txt");
		BufferedReader brA = new BufferedReader(new InputStreamReader(textA));
		BufferedReader brB = new BufferedReader(new InputStreamReader(textB));
		try {
			int r;
			Character prev = null;
			// read training set
			while ((r = brA.read()) != -1) {
				char cur = (char) r;
				train.add(cur);

				// Add stats for unigram
				MutableInteger unigramInitValue = new MutableInteger(1);
				MutableInteger unigramOldValue = trainUnigramStats.put(cur,
						unigramInitValue);

				if (unigramOldValue != null) {
					unigramInitValue.setValue(unigramOldValue.getValue() + 1);
				}

				// Add stats for bigram
				if (prev != null) {
					trainBigramStats.get(cur).Count(prev);
				}
				prev = cur;
			}

			// read test set
			prev = null;
			while ((r = brB.read()) != -1) {
				char cur = (char) r;
				test.add(cur);

				// Add stats for unigram
				MutableInteger unigramInitValue = new MutableInteger(1);
				MutableInteger unigramOldValue = testUnigramStats.put(cur,
						unigramInitValue);

				if (unigramOldValue != null) {
					unigramInitValue.setValue(unigramOldValue.getValue() + 1);
				}

				// Add stats for bigram
				if (prev != null) {
					testBigramStats.get(cur).Count(prev);
				}
				prev = cur;
			}

		} catch (IOException e) {
			e.printStackTrace();
		}

	}

	// Construct tree using
	// Agglomerative Clustering of the Vocabulary
	public void clusteringTree() {
		// Stop only if we have root
		while (clusters.size() != 1) {
			int maxi = 0;
			int maxj = 0;
			double maxScore = Double.NEGATIVE_INFINITY;
			for (int i = 0; i < clusters.size(); i++) {
				for (int j = 0; j < clusters.size(); j++) {
					if (i != j) {
						double score = I(i, j);
						if (score > maxScore) {
							maxScore = score;
							maxi = i;
							maxj = j;
						}
					}
				}
			}
			Cluster Ci = clusters.get(maxi);
			Cluster Cj = clusters.get(maxj);
			// remove old clusters
			clusters.remove(clusters.indexOf(Ci));
			clusters.remove(clusters.indexOf(Cj));
			// System.out.printf("%s, %s \n", Ci.letters, Cj.letters);
			// Add new clusters
			clusters.add(new Cluster(Ci, Cj, maxScore));
		}
		// Get the root of this tree
		root = clusters.get(0);
	}

	public void findBest(int numOfClusters) {
		// Traverse tree using minimum score
		PriorityQueue<Cluster> clu = new PriorityQueue<Cluster>();
		if (numOfClusters < 2) {
			return;
		} else {

			// Add first two
			clu.add(root.left);
			clu.add(root.right);
			while (clu.size() < numOfClusters) {
				Cluster c = clu.poll();
				if (c == null)
					break;
				else {
					clu.add(c.left);
					clu.add(c.right);
				}
			}
		}
		// Print clusters
		Iterator<Cluster> it = clu.iterator();
		while (it.hasNext()) {
			System.out.println(it.next().letters);
		}
	}

	// Compute f score given i, j
	private double F(Cluster k, Cluster m) {
		double score = 0.0;
		Iterator<Character> it = m.letters.iterator();
		// cluster m to be the current character
		while (it.hasNext()) {
			Character cur = it.next();
			// loop over different previous character in cluster k
			Iterator<Character> prevIt = k.letters.iterator();
			while (prevIt.hasNext()) {
				score += trainBigramStats.get(cur).getCount(prevIt.next());
			}
		}
		return score;
	}

	// Computer unigram f score given cluster
	// Simply add them all
	private double F(Cluster k) {
		double score = 0.0;
		Iterator<Character> it = k.letters.iterator();
		while (it.hasNext()) {
			Character cur = it.next();
			score += trainUnigramStats.get(cur).getValue();
		}

		return score;
	}

	// Maximize objective I(i, j)
	private double I(Integer i, Integer j) {
		double score = 0.0;
		Cluster Ci = clusters.get(i);
		Cluster Cj = clusters.get(j);
		// Compute Ck, and Ck with Ci + Cj
		for (int k = 0; k < clusters.size(); k++) {
			if (k != i && k != j) {
				Cluster Ck = clusters.get(k);
				// Compute Ck first
				score += scoreCluster(Ck, new Cluster(Ci, Cj));
				for (int m = 0; m < clusters.size(); m++) {
					if (m != i && m != j) {
						// Only consider if k and m not equals to i,j
						Cluster Cm = clusters.get(m);
						score += scoreCluster(Ck, Cm);
					}
				}
			}
		}

		// Compute Cm with Ci + Cj
		for (int m = 0; m < clusters.size(); m++) {
			if (m != i && m != j) {
				// Only consider if k and m not equals to i,j
				Cluster Cm = clusters.get(m);
				score += scoreCluster(new Cluster(Ci, Cj), Cm);
			}
		}

		// Add Ci + Cj
		score += scoreCluster(new Cluster(Ci, Cj), new Cluster(Ci, Cj));
		// System.out.println(score);
		return score;
	}

	private double scoreCluster(Cluster i, Cluster j) {
		double num = F(i, j);
		double denom = F(i) * F(j);

		// return 0 if F union is 0
		return num == 0 ? 0 : num * log2(num / denom);
	}

	/**
	 * base 2 log utility function
	 * 
	 * @param in
	 * @return
	 */
	public static double log2(double in) {
		return Math.log(in) / Math.log(2);
	}

	/**
	 * Main function
	 * 
	 * @param args
	 */
	public static void main(String[] args) {
		double bitThre = 0.005;
		double chouThre = 0.005;
		// Read args to see if have any specified threshold
		for (int i = 0; i < args.length; i++) {
			try {
		        double thre = Double.parseDouble(args[0]);
		        if(i == 0){
		        	System.out.printf("Setting threshold of Bit-Endocing Tree to be %f \n", thre);
		        	bitThre = thre;
		        }
		        else{
		        	System.out.printf("Setting threshold of Chou Tree to be %f \n", thre);
		        	chouThre = thre;
		        }
		    } catch (NumberFormatException e) {
		        System.err.println("Argument" + args[0] + " must be an double number.");
		        System.exit(1);
		    }
		}

		AggCluster dTree = new AggCluster();
		// Basic clustering
		dTree.clusteringTree();
		System.out.println("================================");
		System.out.println("Best 2 way");
		dTree.findBest(2);
		System.out.println("================================");
		System.out.println("Best 4 way");
		dTree.findBest(4);
		// Build coding-scheme Tree with cluster
		System.out.println("================================");
		System.out.println("Building Bit-Encoding Tree, with threshold : "
				+ bitThre);
		BitEncodingTree bitTree = new BitEncodingTree(dTree.root, bitThre);
		bitTree.buildTree();
		bitTree.perplex();
		// Build Tree using chou's method and Gini-index
		System.out.println("================================");
		System.out.println("Building Tree with Chou's method, with threshod : "
				+ chouThre);
		ChouTree cTree = new ChouTree(chouThre);
		cTree.buildTree();
		cTree.perplex();
	}
}
