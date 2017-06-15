from sklearn import preprocessing
import numpy as np
import sys


if __name__ == '__main__':
	n = int(sys.argv[1])
	with open("./output.txt") as f:
		content = f.readlines()
	content = [x.strip() for x in content] 
	vals = []
	vallst = []
	for c in content:
		if c[0] == 'U':
			c = c.split(" ")
			usrid = c[0].split("'")[1]
			val = c[1].split("[")[1]
			val = val.split("]")[0]
			val = float(val)
			vallst.append(val)
			vals.append([usrid,val])

	#print vals
	#print vallst
	x = np.array(vallst)
	x.reshape(1,-1)
	min_max_scaler = preprocessing.MinMaxScaler()
	x = min_max_scaler.fit_transform(x)
	x = x.tolist()
	for i in range(len(x)):
		vals[i][1] = x[i]

	#print x
	vals = sorted(vals,key =lambda x: x[1])
	vals = vals[0:n]
	#print vals
	thefile = open('results.txt', 'w')

	for item in vals:
		thefile.write(str(item[0])+","+str(item[1])+"\n")


