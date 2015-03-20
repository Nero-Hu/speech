###This repository contains a Fenonic Acoustic Model implementation.

###How to use
- Simply type ``make`` to compile. A "fe" runnable will be generated.
- Usage:
  - -s  to run on test data (default true)
  - -i  iter to define iteration times (default 5 iter)
  - -c  use contrastive model to train (default false)
  - -h  Show this help
- ``make clean`` will clean the binary and ``-o`` files.

###Data files
####Note

- All following files are needed for any training above, since I am simply load file while constructing Models.

- All these files must be placed in the same directory as executable ``fe``.

####Train files
- clsp.lblnames

	Meta information for 256 labels.

- clsp.endpts

	Non-silence segmentation for each training instances.

- clsp.trnlbls

	Sequence of labels for training instances.


- clsp.trnscr

	Training words.

- clsp.trnwav

	Corresponding ``.wav`` filename for words.

####Test files
- clsp.devlbls

	Sequence of labels for testing instances.

- clsp.devwav

	Corresponding ``.wav`` filename for words in test sets.
