/**
 * RIPSimulation.java
 * 	
 * This class simulates a node/router's behavior in real network. we can run this class any number of times from command line.
 * we need to pass a file describing it's neighbors and few more details about the node. and it will automatically create a network that 
 * finds all the route to other nodes in the network and stores them in it's routing table. in case of any node goes down or reappear back it will 
 * automatically update the routes. 
 * 
 * @author Abhishek Panwar
 */


import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.math.BigInteger;
import java.net.DatagramPacket;
import java.net.InetAddress;
import java.net.MulticastSocket;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.Hashtable;

public class RIPSimulation implements Runnable{
	
	private int buoy; //id of buoy, as passed through config file
	private int port; //port number passed through config file
	private InetAddress address = null; //multicast address of the buoy (for sending the data)
	private String fileName=null;  //config file name recieved from command line
	private Hashtable<String, int[]> routingTable = null;  //master routing table
	private Hashtable<InetAddress, Hashtable<String, int[]>> neighborRoutingTables = null; //neighbors routing tables
	boolean broadcast = false; //flag to broadcast immediately in case of time out.
	
	//constructor
	public RIPSimulation(String fileName) throws IOException
	{
		this.fileName = fileName;
		this.routingTable = new Hashtable<>();
		this.neighborRoutingTables = new Hashtable<>();
		this.init();
	}
	
	private void init() throws IOException
	{
		BufferedReader br = null;
		try
		{ //reads the file and initializes the class level variables 
			br = new BufferedReader(new FileReader(this.fileName));
			
			int[] tableData = new int[2]; //contains [Next Hop, Cost]
			String[] s = br.readLine().split(":");
			this.buoy = Integer.parseInt(s[1].trim());
			s = br.readLine().split(":");
			this.port = Integer.parseInt(s[1].trim());
			s = br.readLine().split(":");
			tableData[0] = buoy;
			tableData[1] = 0;
			this.address = InetAddress.getByName(s[1].trim());
			
			s = br.readLine().split(":");
			while(s[0].trim().equals("NEIGHBOR"))
			{ //running receiver threads for each neighbor
				Thread t1 = new Thread(this);
				t1.setName(s[1].trim());
				t1.start();
				s = br.readLine().split(":");
			}
			//updating network address in the master routing table
			while(s[0].trim().equals("NETWORK"))
			{
				//String[] nt = s[1].trim().split("/");
				this.routingTable.put(s[1].trim(), tableData);
				String st=null;
				if ((st = br.readLine())==null)
					break;
				else
					s = st.split(":");
			}
			//running thread for broadcasting the data
			Thread t = new Thread(this);
			t.setName("broadcast");
			t.start();
		}
		finally
		{
			br.close();
		}
	}
	

	public static void main(String[] args) throws IOException{
		//reads the file name from command line and passes over to constructure 
		RIPSimulation obj = null;
		if(args.length>0)
				obj= new RIPSimulation(args[0]);
		else
			System.out.println("no files received");
	}
	
	private void copyIntoRange(byte[] into, byte[] from, int start, int end)
	{
		//System.out.println("copy into range......");
		for(int i=start;i<end;i++)
		{
			into[i] = from[i-start];
		}
	}
	
	public synchronized byte[] createRIPPacket(String command, int version, String ip, int nextHop, int metric) throws UnknownHostException
	{
		byte[] packet = new byte[24];
		int mustBeZero = 0;
		//byte[] ripEntry = new byte[20]; //put into arguments
		//String metric="";
		int addressFamilyIdentifier = this.buoy;
		//System.out.println(addressFamilyIdentifier);
		int subnet = 0;
		String address = "";
		
		if (ip.contains("/")) {
            String[] temp = ip.split("/");
            address = temp[0];
            subnet = Integer.parseInt(temp[1]);
		}
		
		
		copyIntoRange(packet, ByteBuffer.allocate(1).put(command.getBytes()).array(), 0, 1); //command
		copyIntoRange(packet, ByteBuffer.allocate(1).put(Integer.toString(version).getBytes()).array(), 1,2); //version
		copyIntoRange(packet, ByteBuffer.allocate(2).putShort((short)mustBeZero).array(), 2,4);  //mustBeZero
		//copyIntoRange(packet, ByteBuffer.allocate(20).put(ripEntry).array(), 4, 24); //ripEntry
		copyIntoRange(packet, ByteBuffer.allocate(2).putShort((short)addressFamilyIdentifier).array(), 4,6); //addressFamilyIdentifier
		copyIntoRange(packet, ByteBuffer.allocate(2).putShort((short)mustBeZero).array(), 6,8);  //route tag
		
		//System.out.println(address);
		InetAddress add = InetAddress.getByName(address);
		
		//byte[] bytes = ip.getAddress();
		copyIntoRange(packet, ByteBuffer.allocate(4).put(add.getAddress()).array(), 8, 12); //address
		//InetAddress sub = InetAddress.getByName(subnetMask);
		//byte[] bytes = ip.getAddress();
		//copyIntoRange(packet, ByteBuffer.allocate(4).put(sub.getAddress()).array(), 12, 16); //subnet mask
		copyIntoRange(packet, ByteBuffer.allocate(4).putInt(subnet).array(), 12,16);  //subnet mast
		//System.out.println(InetAddress.getByAddress(bytes).getHostAddress());
		copyIntoRange(packet, ByteBuffer.allocate(4).putInt(nextHop).array(), 16,20);  //nextHop
		copyIntoRange(packet, ByteBuffer.allocate(4).putInt(metric).array(), 20,24);  //metric
		
		//System.out.println("creating RIP packet.............");
		
		//packet = Arrays.copyOfRange(ByteBuffer.allocate(2).put(command.getBytes()).array(),0,2); //command
		//packet = Arrays.copyOfRange(ByteBuffer.allocate(2).putShort((short)version).array(),2,4);  //port
		//packet = Arrays.copyOfRange(ByteBuffer.allocate(2).putShort((short)mustBeZero).array(),4,6); //mustBeZero
		//System.out.println(new BigInteger(new String(new byte[] {packet[36], packet[]}).getBytes()).intValue());
		return packet;
		
	}
	
	
	
	
	//broadcast thread runs this method
	public void broadcast() throws IOException, InterruptedException
	{
		DatagramPacket packet;
		MulticastSocket socket = new MulticastSocket(this.port);
		while(true)
		{	//if immediate broadcast flag is off, waits for 5 seconds
			if(!this.broadcast)
				Thread.sleep(5000);
			//convert master routing table into bytes array
			// = convertRoutingTableIntobytes();
			
			printRoutingTable();
			
			for(String ip : this.routingTable.keySet())
			{
				StringBuilder sb = new StringBuilder();
				//String[] str = ip.split("/");
				byte[] buffer = createRIPPacket("r", 2, ip, this.routingTable.get(ip)[0], this.routingTable.get(ip)[1]);
									//String command, int version, String address, int nextHop, int metric
				//byte[] buffer = sb.append(ip+":"+this.routingTable.get(ip)[0]+":"+this.routingTable.get(ip)[1]).toString().getBytes();
			
				
				//creating a datagram packet having multicast address as destination
				packet = new DatagramPacket(buffer, buffer.length, this.address, this.port);
				socket.send(packet); //sends the packet over channel
				
				
			}
			//return sb.toString().getBytes(); //converts string builder into string
			
			this.broadcast = false; //if immediately broadcast flag is on, turn it off
		}
	}
	//all the thread that are listening to neighbors channel run this method
	public void recieve() throws IOException, InterruptedException
	{
		//System.out.println("entered receive...");
		int neighborBouy = this.buoy; //buoy id of the neighbor, initially set to self
		DatagramPacket packet=null;
		InetAddress channel=InetAddress.getByName(Thread.currentThread().getName()); //multicast address is sent using thread name
		
		MulticastSocket socket = new MulticastSocket(this.port);
		socket.joinGroup(channel); //joining the channel for listening the incoming messages
		socket.setSoTimeout(10000); //if no message is received for 10 seconds, it will throw exception
		boolean PacketReceived = false; //checks if any packet received yet or not. if no packets is received i.e. communication 
		//has not started yet and it will not consider as time out. 
		while(true)
		{
			try {
				byte[] buffer = new byte[24];
				packet = new DatagramPacket(buffer, buffer.length);
				socket.receive(packet);
				if(!PacketReceived)
					PacketReceived = true;
				//Hashtable<String, int[]> table = convertPacketIntoRoutingTable(packet); //converting packet received into routing table
				String address = InetAddress.getByAddress((new byte[] {packet.getData()[8],packet.getData()[9],packet.getData()[10],packet.getData()[11]})).getHostAddress();
				int subnet = new BigInteger(new String(new byte[] {packet.getData()[12],packet.getData()[13],packet.getData()[14],packet.getData()[15]}).getBytes()).intValue();
				//System.out.println("address received in packet...."+address);
				int metric = new BigInteger(new String(new byte[] {packet.getData()[20],packet.getData()[21],packet.getData()[22],packet.getData()[23]}).getBytes()).intValue();
				int nextHop = new BigInteger(new String(new byte[] {packet.getData()[16],packet.getData()[17],packet.getData()[18],packet.getData()[19]}).getBytes()).intValue();
				String cidr = address + "/"+Integer.toString(subnet);
				if(neighborBouy==this.buoy) //updates the neighbor's buoy id
				{
					//neighborBouy = findNeighborBuoy(table);
					neighborBouy = new BigInteger(new String(new byte[] {packet.getData()[4], packet.getData()[5]}).getBytes()).intValue();
					
				}
				//updates neighbor routing table and updates master table as well for any new changes or updated routes
				synchronized (this) {
					//this.neighborRoutingTables.put(channel, table);
					//System.out.println("inside synch....");
					if(!this.neighborRoutingTables.contains(channel))
					{
						//System.out.println("channel added for.. "+address);
						int[] arr = new int[] {nextHop, metric};
						Hashtable<String, int[]> tb = new Hashtable<>();
						tb.put(cidr, arr);
						this.neighborRoutingTables.put(channel, tb);
						//this.neighborRoutingTables.get(channel).put(address, arr);
						//System.out.println("updated neighbour table......");
					}
					/*if(!this.neighborRoutingTables.get(channel).contains(address))
					{
						int[] arr = new int[] {nextHop, metric};
						this.neighborRoutingTables.get(channel).put(address, arr);
						//System.out.println("updated neighbour table......");
					}*/
					else
					{
						int[] arr = new int[] {nextHop, metric};
						this.neighborRoutingTables.get(channel).put(cidr, arr);
						//System.out.println("table updated for address..."+address);
						//System.out.println("updated neighbour table......");
					}
					updateMasterTableNew(packet);	
				}
			}//if time out occurs
			catch(SocketTimeoutException e)
			{ //if the communication has not started yet. it will keep on listening again.
				if(PacketReceived)
				{ //if communication has started once and then time out occurred.
					synchronized (this) {
						for(String address : this.neighborRoutingTables.get(channel).keySet())
						{
//							System.out.println("in timeout............ neigh buy  "+ neighborBouy);
//							System.out.println("channel.........."+channel);
//							System.out.println("address........."+address);
//							System.out.println("this.neighborRoutingTables.get(channel).get(address)[1]........."+this.neighborRoutingTables.get(channel).get(address)[1]);
//							System.out.println("this.routingTable.get(address)[0]........."+this.routingTable.get(address)[0]);
							//updates neighbor network address as 16 i.e. unreachable and all the routed going through neighbor are also unreachable
							if(this.neighborRoutingTables.get(channel).get(address)[1]==0 || this.routingTable.get(address)[0]==neighborBouy)
							{
								//System.out.println("went inside too....");
								int[] arr = {this.routingTable.get(address)[0], 16};
								this.routingTable.put(address, arr); //updates routing table
								this.broadcast= true;//in case of time out. immediately broadcast updated routes
							}
						}
					}
					printRoutingTable();//prints routing table in case of time out
					PacketReceived = false; //starts listening again with no packets received
				}
		}
			catch(Exception e)
			{}
		}
		
	}
	
	public synchronized void updateMasterTableNew(DatagramPacket packet) throws UnknownHostException
	{
		//System.out.println("in master table....");
		//printRoutingTable();
		//Hashtable<String, int[]> table = null;
		//int buoy = findNeighborBuoy(table); //neighbor's buoy id
		boolean updated = false; //flag that denotes if the table is updated or not
		
		String address = InetAddress.getByAddress((new byte[] {packet.getData()[8],packet.getData()[9],packet.getData()[10],packet.getData()[11]})).getHostAddress();
		int cost = new BigInteger(new String(new byte[] {packet.getData()[20],packet.getData()[21],packet.getData()[22],packet.getData()[23]}).getBytes()).intValue();
		//int nextHop = new BigInteger(new String(new byte[] {packet.getData()[16],packet.getData()[17],packet.getData()[18],packet.getData()[19]}).getBytes()).intValue();
		int buoy = new BigInteger(new String(new byte[] {packet.getData()[4], packet.getData()[5]}).getBytes()).intValue();
		int subnet = new BigInteger(new String(new byte[] {packet.getData()[12],packet.getData()[13],packet.getData()[14],packet.getData()[15]}).getBytes()).intValue();
		String cidr = address + "/"+Integer.toString(subnet);
		
		//arr[0] = buoy;
		if(cost<16) 
			cost = cost+1; //increase the cost only if it is not set to unreachable
		//int cost = metric;
		int[] arr = new int[] {buoy, cost};
		if(!this.routingTable.containsKey(cidr)) //if the routes doesn't exist in master routing table, add it
		{
			this.routingTable.put(cidr, arr);
			updated = true; //mark as updated
		}
		
		else if(cost < this.routingTable.get(cidr)[1]) //if a new path with lesser cost if found, update it
		{
			this.routingTable.put(cidr, arr);
			updated = true;
		}
		//if a path with unreachable cost, update. 
		else if((cost ==16 || this.routingTable.get(cidr)[1]==16 ) && this.routingTable.get(cidr)[0]==buoy)
		{
			if(this.routingTable.get(cidr)[1]!=16)
			{
				//this.broadcast = true;
				updated = true;
			}
			this.routingTable.put(cidr, arr);
		}
		if(updated) //print the routing table only if the table is updated
			printRoutingTable();
	}

	
	//prints routing table
	public synchronized void printRoutingTable()
	{
		System.out.println("Address		   Next Hop      Cost");
		System.out.println("=======================================");
		for(String ip : this.routingTable.keySet())
		{
			System.out.println(ip+"		"+this.routingTable.get(ip)[0]+"	   "+this.routingTable.get(ip)[1]);
		}
		System.out.println("\n\n");
	}
	//decides which thread to run which method
	public void run()
	{
		if(Thread.currentThread().getName().equals("broadcast"))
			try {
				broadcast();
			} catch (IOException | InterruptedException e) {
			}
		else
			try {
				recieve();
			} catch (IOException | InterruptedException e) {
			}
	}
}

