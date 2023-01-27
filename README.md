# Eosino_Tumor



## Lung Cancer Tissue Analysis Script

These scripts are designed to analyze lung cancer tissue samples using the software QuPath. The first step of theses scripts is to remove anthracose from the images. Then it will divide the images into three regions Outer_external, Outer_internal and tumor for the script expand only and four regions: Tissue, Outer, Inner, and Center for the other script. The script will then detect all cells and positive cells for DAB (eosinophil) in each region. The script will also export all measurements for further analysis in a .tsv file.

## Usage

To run the script, please create a project in QuPath and upload your images. The images must contain an annotation for at least the tissu and the tumor.
Open the script editor on QuPath. "Automate" > " Show script editor"
Open the github page of the script 

[ Script_Eosino_tumor ](https://github.com/AlexHego/Eosino_Tumor/blob/main/Script_Eosino_Tumor.groovy) , BSD-3 licence </br>

[ Script_Eosino_tumor_expandonly ](https://github.com/AlexHego/Eosino_Tumor/blob/main/Script_Eosino_Tumor_expandonly.groovy) , BSD-3 licence </br>

Copy and paste the code in the script editor
Then, simply run the script by selecting "run" > "run for project" in QuPath.


## Output

The script will output a .tsv file for each region containing the number of cells, positive cells for DAB (eosinophil), the area and the number of positive cells per mm².

## Note

Please note that the script is designed for use with lung cancer tissue samples from the GIGA and may not be suitable for other types of tissue or analysis. It is important to validate the results with an expert before making any conclusions.



