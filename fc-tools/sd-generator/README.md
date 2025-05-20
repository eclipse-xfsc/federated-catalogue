# SD Generator



## Getting started

The tool can be used to generate (unsigned!) Self-Descriptions files. Make sure you have Python installed in your env. 
The tool takes 3 input parameters:
    
    1. claimNR: Number of claims per SD
    2. sdNr: Number of created SDs
    3. schema: What type of SD (service or legalperson) 
    
The following example command creates two service SDs with 10 claims each:
```
> python 01_12_createSDnoGraphKeyword.py -claimNr 10 -sdNr 2 -schema service
```