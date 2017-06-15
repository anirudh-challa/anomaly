# Anomaly

There are 3 folders in total:
1. Groovy contains the PSL code along with the data files it needs.
2. Synth contains the synthetic data generated
3. Real contains a subset of the real data we run psl on

Intially the toy1.txt, rated.txt, products.txt and users.txt file in groovy folder belong to the synthetic data. 

In the synth folder you can also use the actual spam users in badusers.txt.

You can clone the repo and change all 4 of these files for the files in real. 

To run PSL:

cd groovy
./run.sh

To get top spammers along with score of truthfulness in a sperpate file called results.txt:

eg:python results.py 4

This will give you the top 4 spammers with the worst truthful scores. You can change this to any number.
