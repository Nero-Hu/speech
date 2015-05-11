package edu.jhu.cs.hthu;

import java.util.HashMap;
import java.util.Map;

import sun.java2d.xr.MutableInteger;

public class Bigram {

	private Character cur;
	private Map<Character, MutableInteger> counter;

	public Bigram(Character cur) {
		this.cur = cur;
		counter = new HashMap<Character, MutableInteger>();
	}

	public int hashCode() {
		return cur != null ? cur.hashCode() : 0;
	}

	public boolean equals(Object other) {
		if (other instanceof Bigram) {
			Bigram otherPair = (Bigram) other;
			return ((this.cur == otherPair.cur || (this.cur != null
					&& otherPair.cur != null && this.cur.equals(otherPair.cur))));
		}

		return false;
	}

	public String toString() {
		return cur.toString();
	}

	/**
	 * Count for stats for previous character given current word
	 * 
	 * @param prev
	 */
	public void Count(Character prev) {
		MutableInteger InitValue = new MutableInteger(1);
		MutableInteger OldValue = counter.put(prev, InitValue);

		if (OldValue != null) {
			InitValue.setValue(OldValue.getValue() + 1);
		}
	}

	public Integer getCount(Character prev) {
		if (counter.containsKey(prev)) {
			return counter.get(prev).getValue();
		}
		return 0;
	}
}
