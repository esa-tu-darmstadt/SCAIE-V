# SCAIE-V
## Welcome to SCAIE-V: An Open-Source SCAlable Interface for ISA Extensions for RISC-V Processors!

## What is SCAIE-V?
SCAIE-V is a: 
- Portable =  supports different microarchitectures
- Scalable = hardware cost scales with the ISAX requirements
- Flexible = supports simple and advanced features (custom control flow, decoupled, multi-cycle instructions, memory instructions)

interface for custom instructions for RISC-V processors. 

## Which cores do you support?
We currently support
- VexRiscv (https://github.com/SpinalHDL/VexRiscv)
- ORCA (https://github.com/cahz/orca)
- Piccolo (https://github.com/bluespec/Piccolo)
- PicoRV32 (https://github.com/YosysHQ/picorv32)

These cores provide different configurations. While testing & evaluating our tool we used the following setup: 
| Core     | Nr. of pipeline stages | Interface |
|----------|------------------------|-----------|
| ORCA     | 5                      | AXI       |
| VexRiscv | 4                      | AHB       |
| Piccolo  | 3                      | AXI       |
| PicoRV32 | non-pipelined          | Native    |

## Which operations are supported in SCAIE-V? 
| Operation     | Meaning | Bitwidth |
|----------|------------------------|-----------|
| RdRS1/2     | Reads operands | 32       |
| RdPC | Reads program counter *                      | 32       |
| RdInstr  | Reads Instruction                      | 32       |
| RdIValid | Status bit informing the custom logic that a certain pipeline stage currently contains an instruction of type X          | 1    |
| WrRD     | Write register file * | 32       |
| Wr/RdMem     | Load/Store operations * | 32       |

\* optionally, the user may request addr. and valid bit for this interface. For WrPC, the user can not request an addr. signal.

## How can I use it for my custom instructions?
Let us consider the following example. The user wants to implement a module which conducts load/stores using custom addresses. He wishes to have a base value for the custom address and then increment/decrement it after/before each load/store. Therefore, he will use an internal register for storing the custom address: `custom_addr`. In order to load the base address into the internal register, he wants to implement a SETADDR custom instruction. For this instruction, he needs to know in which clock cycle he may read the operands from the register file.  He also needs to know when there is a SETADDR instruction in the pipeline, to update the `custom_addr` register. 

### Step 1: 
First, the user has to read the metadata of the core (=when is it allowed to read/update the core's state). He may do this with the following commands: 
```
SCAIEV shim = new SCAIEV();
shim.PrintCoreNames(); // prints the names of the supported cores
shim.PrintCoreInfo("VexRiscv"); // prints metadata for a specific core (using the name from previous command)
```
From the metadata output of one node, only two values are relevant:
- the first value = the earliest clock cycle in which the user may read/update this data
- the third value = the latest clock cycle in which the user may read/update this data

The rest of the values are currently used in internal research projects. 

### Step 2: 
Using the metadata in Step 1, the user decides in which clock cycles to read/update core's state. The custom ISAX module has to be designed based on this information. In our simple example, it would be something like: 
```verilog
module SETADDR (
    input        clk_i,
    input        rdIValid_SETADDR_2_i, 
    input [31:0] rdRS1_2_i, 
   //..value of custom regs as outputs used by other ISAXes. Inputs from other ISAXes to update custom_addr after/before a load/store
   ); 
    reg [31:0] custom_addr; 
    always @(posedge clk_i) begin 
        if(rdIValid_SETADDR_2_i)
            custom_addr <= rdRS1_2_i ;
       // rest of the logic for updating custom_addr in case of load/store ISAXes
    end 
endmodule 
```
### Step 3: 
The third step implies generating the custom instructions interface using the SCAIE-V tool. Let us consider that the user decided to read operands in the third cycle (numbering starts at 0). He does not have to modify anything in the core, but just let SCAIE-V do the work: 
```
SCAIEV shim = new SCAIEV();
SCAIEVInstr setaddr  = shim.addInstr("SETADDRGEN","-------", "000", "0001011", "I");  
setaddr.PutSchedNode(FNode.RdRS1, 2);  
setaddr.PutSchedNode(FNode.RdIValid, 2); // valid bit for updating the custom_addr register
shim.Generate("VexRiscv"); // generates all the code
```
The files of the VexRiscv core will be modified so that it supports the new interface.

## What is the current status of the project? 
The project is quite new and we are constantly working on improving it & testing it with different configurations. 

## This ReadMe does not help me much. Where can I find more information? 
This is the first version of the ReadMe file. In the following weeks we will upload a more detailed version. 

## Is there a paper to this work? 
Mihaela Damian, Julian Oppermann, Christoph Spang, Andreas Koch, "SCAIE-V: An Open-Source SCAlable Interface for ISA Extensions
for RISC-V Processors"

## Do you have further questions?
For any questions, remarks or complaints, you can reach me at  damian@esa.tu-darmstadt.de. :) 
