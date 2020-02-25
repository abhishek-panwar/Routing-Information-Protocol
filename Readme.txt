# RIP Simulation


RIP packet format that I have created.

0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5 6 7 8 9 0 1
+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
| command (1) | version (1) | must be zero (2) |
+---------------+---------------+-------------------------------+
| |
˜ 			RIP Entry (20) as below 
| |
+---------------+---------------+---------------+---------------+
+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
| address family identifier (2) | route tag (2) |
+-------------------------------+-------------------------------+
| 				IPv4 address (4) |
+---------------------------------------------------------------+
| 				subnet mask (4) |
+---------------------------------------------------------------+
| 				next hop (4) |
+---------------------------------------------------------------+
| 				metric (4) |
+---------------------------------------------------------------+


where command is 'r' for request.
veresion is 2
must be zero is 0
address family identifier is  buoy id
route tag is 0 for now as I have no idea what it is
IPV4 address as stored in routing table each entry
subnet mask is int value of prefix length
next hop as given in routing table
metric is cost as in routing table


Note:
The program focuses on implementing the RIP packet format. 
total packet size I have taken is 24 bytes. where each packet contains a single entry
of routing table.



