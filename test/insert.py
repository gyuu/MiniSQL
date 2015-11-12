# 用于生成插入数据的 SQL 脚本。
# 在 MiniSQL 中使用 execfile filename; 执行。

from random import sample, uniform
from string import ascii_letters as CHARS

def main():
	record_num = input("How many records do you want?\n")
	f = open("test.sql", "w")
	CREATE = "create table Person ( id int, name char(5), salary float, primary key (id) );"
	f.write(CREATE+'\n')

	for i in range(int(record_num)):
		name = "".join(sample(CHARS, 5))
		salary = str(uniform(1, 100))[:4]
		insert = "insert into Person values (" + str(i) + ", '" + name + "', " + salary + ");"
		f.write(insert+'\n')

if __name__ == '__main__':
	main()
