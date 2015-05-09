package edu.jhu.cs.hthu;

import java.util.ArrayList;
import java.util.List;

/**
 * Cluster class
 * 
 * @author nerohu
 *
 */
public class Cluster implements Comparable<Cluster> {
	// List of letters that contains in this cluster
	List<Character> letters;
	double score;
	Cluster parent;
	// Left right child
	Cluster left;
	Cluster right;

	/**
	 * Base case constructor
	 * 
	 * @param letter
	 */
	public Cluster(Character letter) {
		letters = new ArrayList<Character>();

		// Add letter
		letters.add(letter);
	}

	/**
	 * Construct a tree by merging
	 * 
	 * @param i
	 * @param j
	 */
	public Cluster(Cluster i, Cluster j) {
		i.parent = this;
		j.parent = this;
		this.left = i;
		this.right = j;

		// Add letters by merging two letters list
		this.letters = new ArrayList<Character>();
		letters.addAll(i.letters);
		letters.addAll(j.letters);
	}

	/**
	 * With score
	 * 
	 * @param i
	 * @param j
	 * @param score
	 */
	public Cluster(Cluster i, Cluster j, double score) {
		this(i, j);
		this.score = score;
	}

	public int compareTo(Cluster o) {
		if (this.score < o.score)
			return -1;
		else if (this.score > o.score)
			return 1;
		return 0;
	}
}