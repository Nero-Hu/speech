OBJS = fe.o
CC = g++
DEBUG = -g
CFLAGS = -Wall -c $(DEBUG)
LFLAGS = -Wall $(DEBUG)

fe : $(OBJS)
	$(CC) $(LFLAGS) $(OBJS) -o fe

fe.o : hmm.h fenonic.h fe.cc
	$(CC) $(CFLAGS) fe.cc

clean:
	\rm *.o fe

