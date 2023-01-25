# Eosino_Tumor

 **************************************************************************
 Goal:
 
 0) import libraries and set the variables 
 1) Set the vectors stain
 2) Detect the bubbles and merges them together
 2) Detect the pollution particles and merge the bubbles + pollution annotation, rename it bubbles
 3) remove the area of the annotation Bulle from the area of annotation Tissu
 3) rename class TissuBis in Tissu
 4) Script to help with annotating tumor regions, separating the tumor margin from the center. 
 5) remove annotation Inner, Center from the area of annotation Tissu
 6) Detect the pollution particles
 7) remove the area of pollution from the area of annotation Tissu
 8) remove the area of pollution from the area of Outer
 9) remove the area of pollution from the area of Inner
 9) remove the area of pollution from the area of Center
 10) Clear the pollution annotations
 11) Detect the positive cells in tissu, outer, inner, center zones
 12) Save the annotations
 **************************************************************************
 Tutorial
 
 0) Possibility to change the model for anthracose a,d the size of the margin for inner, outer zones
 1) Set the stainning vectors for the current batch 
 2) use the brush tool to annotate the bubbles, or all the zone we want to exclude and set them in class Bulle
 3) use the brush tool to annotate the tissue and set it in class Tissu
 4) use the brush tool to annotate the tumor and set it in class Tumor 
 Warning the Tumor zone need a size 2x of the expandMarginMicrons variable
 5) Select Run > Run for project
 **************************************************************************

