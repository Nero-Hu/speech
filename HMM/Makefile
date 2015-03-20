OBJS = main.o
CC = g++
DEBUG = -g
CFLAGS = -Wall -c $(DEBUG)
LFLAGS = -Wall $(DEBUG)

hmm : $(OBJS)
	$(CC) $(LFLAGS) $(OBJS) -o hmm

main.o : hmm.h main.cc
	$(CC) $(CFLAGS) main.cc

clean:
	\rm *.o hmm

