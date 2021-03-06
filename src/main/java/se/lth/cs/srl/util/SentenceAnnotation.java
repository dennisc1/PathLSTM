package se.lth.cs.srl.util;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import se.lth.cs.srl.corpus.Predicate;
import se.lth.cs.srl.corpus.Sentence;
import se.lth.cs.srl.corpus.Word;
import se.lth.cs.srl.corpus.Yield;

public class SentenceAnnotation {

	public List<String[]> conceptAnno;
	public List<String[]> relationAnno;
	int startIndex;
	int endIndex;

	Map<String, List<Integer[]>> concept2spans;
	Map<String, Word> concept2word;
	Map<String, List<Word>> concept2words;

	Map<String, String> concept2label;
	List<List<Integer>> wordids;
	private Map<String, Integer> id2conceptAnno;

	public SentenceAnnotation(int startIndex, int endIndex) {
		this.startIndex = startIndex;
		this.endIndex = endIndex;
		conceptAnno = new LinkedList<>();
		relationAnno = new LinkedList<>();
		id2conceptAnno = new HashMap<>();
	}

	public void addAnnotation(String[] num_anno_word) {
		if (num_anno_word.length == 3) {
			if (id2conceptAnno.containsKey(num_anno_word[0])
					&& num_anno_word[0].startsWith("F")) {
				int index = id2conceptAnno.get(num_anno_word[0]);
				String[] anno = conceptAnno.remove(index);
				String[] name_from_to1 = anno[1].split(" ");
				String[] name_from_to2 = num_anno_word[1].split(" ");

				int from1 = Integer.parseInt(name_from_to1[1]);
				int from2 = Integer.parseInt(name_from_to2[1]);
				int to1 = Integer.parseInt(name_from_to1[2]);
				int to2 = Integer.parseInt(name_from_to2[2]);
				if (to1 + 1 == from2) {
					anno[1] = name_from_to1[0] + " " + from1 + " " + to2;
				} else if (to2 + 1 == from1) {
					anno[1] = name_from_to1[0] + " " + from2 + " " + to1;
				} else
					anno[1] = name_from_to1[0] + " " + from1 + " " + to1 + " "
							+ from2 + " " + to2;
				// anno[1] = name_from_to1[0] + " " + (from1<from2?from1:from2)
				// + " " + (to1>to2?to1:to2);
				conceptAnno.add(index, anno);
			} else {
				id2conceptAnno.put(num_anno_word[0], conceptAnno.size());
				conceptAnno.add(num_anno_word);
			}
		} else {
			relationAnno.add(num_anno_word);
		}
	}

	private int merge(String[] num_anno_word, Integer integer) {
		return 0;
	}

	public void apply(Sentence sen, boolean framenet) {
		wordids = new LinkedList<>();

		int[] begins = new int[sen.size() - 1];
		int[] ends = new int[sen.size() - 1];
		for (int i = 0; i < begins.length; i++) {
			Word w = sen.get(i + 1);
			if (i > 0)
				System.err.print(" ");
			System.err.print(w.getForm());
			begins[i] = w.getBegin();
			ends[i] = w.getEnd() - (framenet ? 1 : 0);
			/** XXX: if FN, use w.getEnd()-1 instead? **/
		}
		System.err.println();

		concept2spans = new HashMap<>();
		concept2word = new HashMap<>();
		concept2words = new HashMap<>();
		concept2label = new HashMap<>();
		TreeMap<Integer, String> pred2sense = new TreeMap<>();

		int last_target = -1;
		for (String[] anno : conceptAnno) {
			// System.out.println(Arrays.toString(anno));

			String[] parts = anno[1].split(" ");
			String label = parts[0];

			List<Integer> currids = new LinkedList<>();
			for (int i = 1; i + 1 < parts.length; i += 2) {
				int startCharacter = Integer.parseInt(anno[1].split(" ")[i]);
				int endCharacter = Integer.parseInt(anno[1].split(" ")[i + 1]);

				for (int j = 0; j < begins.length; j++) {
					if (begins[j] >= startCharacter && ends[j] <= endCharacter)
						currids.add(j);
				}
				if (currids.size() == 0) {
					System.err.println(sen.toString());
					System.err
							.println("Error: no matching token found for span from "
									+ startCharacter + ":" + endCharacter);
					if (!framenet) {
						System.err.println("Tokens are:");
						for (int j = 0; j < begins.length; j++) {
							System.err.println("  " + begins[j] + ":" + ends[j]
									+ "\t" + sen.get(j + 1).getForm());
						}
						continue;
					}
					for (int j = 0; j < begins.length; j++) {
						if (begins[j] >= startCharacter
								&& endCharacter <= ends[j]) {
							System.err
									.println("Mapped to first surrounding token instead ("
											+ begins[j] + ":" + ends[j] + ")");
							currids.add(j);
							break;
						}
					}
				}
			}
			// System.out.println(anno[0]);

			if (!concept2spans.containsKey(anno[0]))
				concept2spans.put(anno[0], new LinkedList<>());
			else {
				System.err.println("Concept appeared twice?! " + anno[0]);
			}
			concept2spans.get(anno[0]).add(
					currids.toArray(new Integer[currids.size()]));
			concept2label.put(anno[0], label);
		}
		for (String anno : concept2spans.keySet()) {
			String label = concept2label.get(anno);

			if (framenet && anno.startsWith("F")) {
				List<Integer> currids = new LinkedList<>();
				for (Integer[] ids : concept2spans.get(anno))
					for (Integer i : ids)
						currids.add(i);

				last_target = head(sen, currids);
				concept2word.put(anno, sen.get(last_target + 1));
				pred2sense.put(sen.get(last_target + 1).getIdx(), label);

				// System.err.print("Frame ("+anno[1].split(" ")[0]+"): " +
				// sen.get(head(sen, currids)+1).getForm());
			} else {
				/*for (Integer[] ids : concept2spans.get(anno)) {
					List<Integer> currids = new LinkedList<Integer>();
					
					for(int i : ids) {
						if(sen.get(i+1).getDeprel().equals("PUNCT")) continue;
						currids.add(i);
					}
									
					int[] ws = heads(sen, currids);
					
					if (ws.length == 1) {
						Word w = sen.get(ws[0] + 1);
						concept2word.put(anno, w);
						if (label.equals("Object")
								&& (w.getPOS().equals("TO") || w.getPOS()
										.equals("IN"))
								&& !w.getChildren().isEmpty())
							concept2word.put(anno, w.getChildren().iterator()
									.next());
					} else {
						concept2words.put(anno, new LinkedList<>());
						for (Integer i : ws) {
							Word w = sen.get(i + 1);
							concept2words.get(anno).add(w);
						}
					}
				}*/
			}
		}

		// mark predicates and collect predicate-argument relationships
		List<Relation> relations = new LinkedList<>();

		for (String[] anno : relationAnno) {
			String[] rel_arg1_arg2 = anno[1].split(" ");
			String rel = rel_arg1_arg2[0];
			String arg1 = rel_arg1_arg2[1].split(":")[1];
			String arg2 = rel_arg1_arg2[2].split(":")[1];

			if (framenet) {
				if (concept2word.containsKey(arg1)) {
					int predIndex = concept2word.get(arg1).getIdx();
					/*sen.makePredicate(predIndex);
					pred2sense.put(predIndex, rel.split(":")[0]);
					((Predicate) sen.get(predIndex)).setSense(pred2sense.get(predIndex));*/
					
					//System.err.print(sen.get(predIndex).getForm() + "[" + rel.split(":")[0] + "] --" + rel.split(":")[1] + "-> ");
					
					List<Word> currids = new LinkedList<Word>();
					for (Integer[] ids : concept2spans.get(arg2))
						for (Integer i : ids) {
							// skip punctuation
							if(!sen.get(i+1).getDeprel().equals("PUNCT")) 
								currids.add(sen.get(i+1));
						}
					
					boolean fail = true;
					for(Word w : currids) {
						Yield y = w.getYield(sen.get(predIndex), "", Collections.singleton(w));
						if(y.containsAll(currids)) {
							//System.err.println(" " + w.getForm());
							concept2word.put(arg2, w);
							relations.add(new Relation(concept2word.get(arg1).getIdx(), concept2word.get(arg2).getIdx(), 
									rel.split(":")[1]));
							fail = false;
							break;
						}
					}
					if(!fail) continue;
						
					boolean phrases = false;
					for(Word w : currids) {
						if(w.getChildren().size()>0) { phrases = true; break; }
					}
					
					if(!phrases) {
						for(Word w : currids) {
							relations.add(new Relation(concept2word.get(arg1).getIdx(), w.getIdx(), rel.split(":")[1]));
							//System.err.print(" " + w.getForm());
						}
						fail = false;
						//System.err.println();
					}
					if(!fail) continue;

					// last resort: first "highest" word in span
					int highest = Integer.MAX_VALUE;
					Word best = null;
					for(Word w : currids) {
						int depth = w.pathToRoot(w, new LinkedList<Word>()).size();
						if(depth < highest) {
							highest = depth;
							best = w;
						}
							
					}
					relations.add(new Relation(concept2word.get(arg1).getIdx(), best.getIdx(), rel.split(":")[1]));			
					
					//System.err.println("? " + best.getForm()  + " " +Arrays.toString(concept2spans.get(arg2).get(0)));
				}
			} else {

				if (concept2word.get(arg1) == null
						|| concept2word.get(arg2) == null)
					continue; // HACK

				int arg1index = concept2word.get(arg1).getIdx();
				int arg2index = concept2word.get(arg2).getIdx();

				String l1 = concept2label.get(arg1);
				String l2 = concept2label.get(arg2);

				/** for S-CASE **/
				if (l1.equals("Action")
						&& l2.equals("Object")
						&& ((sen.get(arg2index).getPOS().equals("TO") || sen
								.get(arg2index).getPOS().equals("IN")) && !sen
								.get(arg2index).getChildren().isEmpty()))
					arg2index = sen.get(arg2index).getChildren().iterator()
							.next().getIdx();
				if (l1.equals("Object")
						&& ((sen.get(arg1index).getPOS().equals("TO") || sen
								.get(arg1index).getPOS().equals("IN")) && !sen
								.get(arg1index).getChildren().isEmpty()))
					arg1index = sen.get(arg1index).getChildren().iterator()
							.next().getIdx();

				if (l1.equals("Action") && l2.equals("Object")) {
					pred2sense.put(arg1index, "Action");
					relations.add(new Relation(arg1index, arg2index, "Theme"));
				} else if (l1.equals("Actor") && l2.equals("Action")) {
					pred2sense.put(arg2index, "Action");
					relations.add(new Relation(arg2index, arg1index, "Actor"));
				} else if (l1.equals("Action") && l2.equals("Property")) {
					pred2sense.put(arg1index, "Action");
					relations
							.add(new Relation(arg1index, arg2index, "Property"));
				} else if (l2.equals("Property")) {
					pred2sense.put(arg1index, l1);
					relations
							.add(new Relation(arg1index, arg2index, "Property"));
				} /**/
			}
		}

		// add predicates/senses (TreeMap.keySet() is in ascending ordered!)
		for (Integer i : pred2sense.keySet()) {
			sen.makePredicate(i);
			((Predicate) sen.get(i)).setSense(pred2sense.get(i));
		}

		// add predicate-argument relationships
		for (Relation r : relations) {
			((Predicate) sen.get(r.head)).addArgMap(sen.get(r.dependent), r.label);
			// System.out.println(sen.get(r.head).getForm() + "["+r.label+"]: "
			// + sen.get(r.dependent).getForm());
		}
	}

	private boolean contains(Sentence sen, int head, int last_target) {
		List<Integer> todo = new LinkedList<>();
		List<Integer> done = new LinkedList<>();
		todo.add(head);

		while (!todo.isEmpty()) {
			int curr = todo.remove(0);
			if (curr == last_target)
				return true;

			done.add(curr);
			for (Word w : sen.get(curr + 1).getChildren()) {
				int child = w.getIdx() - 1;
				if (done.contains(child))
					continue;
				todo.add(child);
			}
		}

		return false;
	}

	private int[] heads(Sentence sen, List<Integer> currids) {
		if (currids.size() == 1)
			return new int[] { currids.get(0) };
		if (sen.get(currids.get(0) + 1).getPOS().startsWith("V"))
			return new int[] { currids.get(0) };

		boolean containsSubtrees = false;
		for (Integer i : currids)
			if (!sen.get(i+1).getDeprel().equals("PUNCT") && sen.get(i + 1).getSpan().size() > 1)
				containsSubtrees = true;

		if (containsSubtrees) {
			if (sen.get(currids.get(0) + 1).getPOS().startsWith("J"))
				return new int[] { currids.get(currids.size() - 1) };
			if (sen.get(currids.get(0) + 1).getPOS().startsWith("N")) {
				for (Integer i : currids) {
					if (sen.get(i + 1).getForm().equals("of"))
						return new int[] { currids.get(0) };
				}
			}

			for (int i = currids.size() - 1; i >= 0; i--) {
				int newhead = sen.get(currids.get(i) + 1).getHeadId() - 1;
				if (!currids.contains(newhead))
					return new int[] { currids.get(i) };
			}
		}

		int[] retval = new int[currids.size()];
		int i = 0;
		for (int x : currids)
			retval[i++] = x;

		return retval;

	}

	public int head(Sentence sen, List<Integer> currids) {
		if (currids.size() == 1)
			return currids.get(0);
		if (sen.get(currids.get(0) + 1).getPOS().startsWith("V"))
			return currids.get(0);
		if (sen.get(currids.get(0) + 1).getPOS().startsWith("J"))
			return currids.get(currids.size() - 1);
		if (sen.get(currids.get(0) + 1).getPOS().startsWith("N")) {
			for (Integer i : currids) {
				if (sen.get(i + 1).getForm().equals("of"))
					return currids.get(0);
			}
		}

		for (int i = currids.size() - 1; i >= 0; i--) {
			int newhead = sen.get(currids.get(i) + 1).getHeadId() - 1;
			if (!currids.contains(newhead))
				return currids.get(i);
		}
		return -1;

		/*
		 * int head = currids.get(0);
		 * 
		 * for(int i=1; i<currids.size(); i++) { if(currids.get(i)>head)
		 * head=currids.get(i); }
		 * 
		 * boolean containshead = true; while(containshead) { int newhead =
		 * sen.get(head+1).getHeadId()-1; containshead = false; for(int i=0;
		 * i<currids.size(); i++) if(currids.get(i)==newhead) containshead =
		 * true; if(containshead) head = newhead; }
		 * 
		 * return head;
		 */
	}

}
