/*
January 2023, Qupath version  0.4.1
BSD 3-Clause License
@author Alexandre Hego
 
 contact: alexandre.hego@uliege.be
 GIGA Cell imaging facility
 University of Liege 

 **************************************************************************
 Goal:
 0) import libraries and set the variables 
 1) Set the vectors stain
 2) Detect the bubbles and merges them together
 3) remove the area of the annotation Bulle from the area of annotation Tissu
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
 */


/* 0) import libraries and set the variables 
****************************************************/
import org.locationtech.jts.geom.Geometry
import qupath.lib.common.GeneralTools
import qupath.lib.objects.PathObject
import qupath.lib.objects.PathObjects
import qupath.lib.roi.GeometryTools
import qupath.lib.roi.ROIs
import static qupath.lib.gui.scripting.QPEx.*

// Set the model of random forest to detect anthracose
model_anthracose = "18-01-2023- 2-detection anthracose sur Batch Very hight - Copie avec etoile"

// How much to expand each region
double expandMarginMicrons = 200

// Define the colors
def coloInnerMargin = getColorRGB(0, 0, 200)
def colorOuterMargin = getColorRGB(0, 200, 0)
def colorCentral = getColorRGB(200, 0, 0)

/* 1)  Set the vectors stain
****************************************************/
setColorDeconvolutionStains('{"Name" : "H-DAB estimated", "Stain 1" : "Hematoxylin", "Values 1" : "0.66034 0.69836 0.27614", "Stain 2" : "DAB", "Values 2" : "0.50613 0.58015 0.63817", "Background" : " 187 182 174"}');


/* 2) Detect the bubbles or Tumor and merges them together
****************************************************/
selectObjectsByClassification("Bulle");
mergeSelectedAnnotations();

selectObjectsByClassification("Tumor");
mergeSelectedAnnotations();


/* 3) remove the area of the annotation Bulle from the area of annotation Tissu
***************************************************************************************************/
tissueAnnotation = getAnnotationObjects().find{it.getPathClass() == getPathClass("Tissu")}
tissueGeom = tissueAnnotation.getROI().getGeometry()

//Cycle through Bulle annotations and subtract them from the tissue
getAnnotationObjects().findAll{it.getPathClass() == getPathClass("Bulle")}.each{anno->
    currentGeom = anno.getROI().getGeometry()
    //Note the ! which means we are looking for NOT intersects
    tissueGeom = tissueGeom.difference(currentGeom)
}

//Create the new object
tissueROI = GeometryTools.geometryToROI(tissueGeom, ImagePlane.getDefaultPlane())
newTissue = PathObjects.createAnnotationObject( tissueROI, getPathClass("Tissubis") )
addObject(newTissue)
fireHierarchyUpdate()

selectObjectsByClassification("Tissu")
clearSelectedObjects(false)
selectObjectsByClassification("Bulle")
clearSelectedObjects(false)

def currentClass = getPathClass("Tissubis")  
def newClass = getPathClass("Tissu")
getAnnotationObjects().each { annotation ->  if (annotation.getPathClass().equals(currentClass)) annotation.setPathClass(newClass)}


/* 4) Script to help with annotating tumor regions, separating the tumor margin from the center.
 modify from Pete Bankhead
***************************************************************************************************/
// Choose whether to lock the annotations or not (it's generally a good idea to avoid accidentally moving them)
def lockAnnotations = true

// Extract the main info we need
def imageData = getCurrentImageData()
def hierarchy = imageData.getHierarchy()
def server = imageData.getServer()

// We need the pixel size
def cal = server.getPixelCalibration()
if (!cal.hasPixelSizeMicrons()) {
  print 'We need the pixel size information here!'
  return
}
if (!GeneralTools.almostTheSame(cal.getPixelWidthMicrons(), cal.getPixelHeightMicrons(), 0.0001)) {
  print 'Warning! The pixel width & height are different; the average of both will be used'
}

// Get annotation & detections
hierarchy = getCurrentHierarchy()
annotation = getAnnotationObjects().findAll{it.getPathClass() == getPathClass("Tumor")}
hierarchy.getSelectionModel().setSelectedObject(annotation)

def annotations = getAnnotationObjects()
def selected = getSelectedObject()
if (selected == null || !selected.isAnnotation()) {
  print 'Please select an annotation object!'
  return
}

// We need one selected annotation as a starting point; if we have other annotations, they will constrain the output
annotations.remove(selected)

// Extract the ROI & plane
def roiOriginal = selected.getROI()
def plane = roiOriginal.getImagePlane()

// If we have at most one other annotation, it represents the tissue
Geometry areaTissue
PathObject tissueAnnotation
if (annotations.isEmpty()) {
  areaTissue = ROIs.createRectangleROI(0, 0, server.getWidth(), server.getHeight(), plane).getGeometry()
} else if (annotations.size() == 1) {
  tissueAnnotation = annotations.get(0)
  areaTissue = tissueAnnotation.getROI().getGeometry()
} else {
  print 'Sorry, this script only support one selected annotation for the tumor region, and at most one other annotation to constrain the expansion'
  return
}

// Calculate how much to expand
double expandPixels = expandMarginMicrons / cal.getAveragedPixelSizeMicrons()
def areaTumor = roiOriginal.getGeometry()

// Get the outer margin area
def geomOuter = areaTumor.buffer(expandPixels)
geomOuter = geomOuter.difference(areaTumor)
geomOuter = geomOuter.intersection(areaTissue)
def roiOuter = GeometryTools.geometryToROI(geomOuter, plane)
// def annotationOuter = PathObjects.createAnnotationObject(roiOuter)
def annotationOuter = PathObjects.createAnnotationObject(roiOuter, getPathClass("Outer"))
annotationOuter.setName("Outer margin")
annotationOuter.setColorRGB(colorOuterMargin)
addObject(annotationOuter)

// Get the central area
def geomCentral = areaTumor.buffer(-expandPixels)
geomCentral = geomCentral.intersection(areaTissue)
def roiCentral = GeometryTools.geometryToROI(geomCentral, plane)
// change def annotationCentral = PathObjects.createAnnotationObject(roiCentral)
def annotationCentral = PathObjects.createAnnotationObject(roiCentral, getPathClass("Center"))
annotationCentral.setName("Center")
annotationCentral.setColorRGB(colorCentral)
addObject(annotationCentral)

// Get the inner margin area
def geomInner = areaTumor
geomInner = geomInner.difference(geomCentral)
geomInner = geomInner.intersection(areaTissue)
def roiInner = GeometryTools.geometryToROI(geomInner, plane)
// def annotationInner = PathObjects.createAnnotationObject(roiInner)
def annotationInner = PathObjects.createAnnotationObject(roiInner, getPathClass("Inner"))
annotationInner.setName("Inner margin")
annotationInner.setColorRGB(coloInnerMargin)
addObject(annotationInner)

// remove the tumor object
selectObjectsByClassification("Tumor")
clearSelectedObjects(false)


/* 5) remove annotation Inner, Center from the area of annotation Tissu
***************************************************************************************************/
tissueAnnotation = getAnnotationObjects().find{it.getPathClass() == getPathClass("Tissu")}
tissueGeom = tissueAnnotation.getROI().getGeometry()

//Cycle through Bulle annotations and subtract them from the tissue
getAnnotationObjects().findAll{it.getPathClass() != getPathClass("Tissu")}.each{anno->
    currentGeom = anno.getROI().getGeometry()
    //Note the ! which means we are looking for NOT intersects
    tissueGeom = tissueGeom.difference(currentGeom)
}

//Create the new object
tissueROI = GeometryTools.geometryToROI(tissueGeom, ImagePlane.getDefaultPlane())
newTissue = PathObjects.createAnnotationObject( tissueROI, getPathClass("Tissubis") )
addObject(newTissue)
fireHierarchyUpdate()

// remove the tumor object
selectObjectsByClassification("Tissu")
clearSelectedObjects(false)

//rename class TissuBis in Tissu
getAnnotationObjects().each { annotation ->  if (annotation.getPathClass().equals(currentClass)) annotation.setPathClass(newClass)}

/* 6) Detect the pollution particles 
***************************************************************************************************/
selectAnnotations();
createAnnotationsFromPixelClassifier(model_anthracose, 1.0, 5.0)
selectObjectsByClassification("pollution")
mergeSelectedAnnotations();

resultingClass = getPathClass("pollution")
toChange = getAnnotationObjects().findAll{it.getPathClass() == null}
toChange.each{ it.setPathClass(resultingClass)}

////////////////////////////////////////////////////////////////////////////
// reclaim memory from the classifier
javafx.application.Platform.runLater {
getCurrentViewer().getImageRegionStore().cache.clear()
System.gc()
}
Thread.sleep(10)

/////////////////////////////////////////////////////////////////////////////

/* 7) remove the area of pollution from the area of annotation Tissu
***************************************************************************************************/
tissueAnnotation = getAnnotationObjects().find{it.getPathClass() == getPathClass("Tissu")}
tissueGeom = tissueAnnotation.getROI().getGeometry()

//Cycle through Bulle annotations and subtract them from the tissue
getAnnotationObjects().findAll{it.getPathClass() == getPathClass("pollution")}.each{anno->
    currentGeom = anno.getROI().getGeometry()
    //Note the ! which means we are looking for NOT intersects
    tissueGeom = tissueGeom.difference(currentGeom)
}

//Create the new object
tissueROI = GeometryTools.geometryToROI(tissueGeom, ImagePlane.getDefaultPlane())
newTissue = PathObjects.createAnnotationObject( tissueROI, getPathClass("Tissubis") )
addObject(newTissue)
fireHierarchyUpdate()

selectObjectsByClassification("Tissu")
clearSelectedObjects(false)
getAnnotationObjects().each { annotation ->  if (annotation.getPathClass().equals(currentClass)) annotation.setPathClass(newClass)}

/* 8) remove the area of pollution from the area of annotation Outer
***************************************************************************************************/
tissueAnnotation = getAnnotationObjects().find{it.getPathClass() == getPathClass("Outer")}
tissueGeom = tissueAnnotation.getROI().getGeometry()

//Cycle through Bulle annotations and subtract them from the tissue
getAnnotationObjects().findAll{it.getPathClass() == getPathClass("pollution")}.each{anno->
    currentGeom = anno.getROI().getGeometry()
    //Note the ! which means we are looking for NOT intersects
    tissueGeom = tissueGeom.difference(currentGeom)
}

//Create the new object
tissueROI = GeometryTools.geometryToROI(tissueGeom, ImagePlane.getDefaultPlane())
newTissue = PathObjects.createAnnotationObject( tissueROI, getPathClass("Outerbis") )
addObject(newTissue)
fireHierarchyUpdate()

selectObjectsByClassification("Outer")
clearSelectedObjects(false)
currentClass = getPathClass("Outerbis")  
newClass = getPathClass("Outer")
getAnnotationObjects().each { annotation ->  if (annotation.getPathClass().equals(currentClass)) annotation.setPathClass(newClass)}

/* 9) remove the area of pollution from the area of annotation Inner
***************************************************************************************************/
tissueAnnotation = getAnnotationObjects().find{it.getPathClass() == getPathClass("Inner")}
tissueGeom = tissueAnnotation.getROI().getGeometry()

//Cycle through Bulle annotations and subtract them from the tissue
getAnnotationObjects().findAll{it.getPathClass() == getPathClass("pollution")}.each{anno->
    currentGeom = anno.getROI().getGeometry()
    //Note the ! which means we are looking for NOT intersects
    tissueGeom = tissueGeom.difference(currentGeom)
}

//Create the new object
tissueROI = GeometryTools.geometryToROI(tissueGeom, ImagePlane.getDefaultPlane())
newTissue = PathObjects.createAnnotationObject( tissueROI, getPathClass("Innerbis") )
addObject(newTissue)
fireHierarchyUpdate()

selectObjectsByClassification("Inner")
clearSelectedObjects(false)
currentClass = getPathClass("Innerbis")  
newClass = getPathClass("Inner")
getAnnotationObjects().each { annotation ->  if (annotation.getPathClass().equals(currentClass)) annotation.setPathClass(newClass)}

/* 9) remove the area of pollution from the area of annotation Inner
***************************************************************************************************/
tissueAnnotation = getAnnotationObjects().find{it.getPathClass() == getPathClass("Center")}
tissueGeom = tissueAnnotation.getROI().getGeometry()

//Cycle through Bulle annotations and subtract them from the tissue
getAnnotationObjects().findAll{it.getPathClass() == getPathClass("pollution")}.each{anno->
    currentGeom = anno.getROI().getGeometry()
    //Note the ! which means we are looking for NOT intersects
    tissueGeom = tissueGeom.difference(currentGeom)
}

//Create the new object
tissueROI = GeometryTools.geometryToROI(tissueGeom, ImagePlane.getDefaultPlane())
newTissue = PathObjects.createAnnotationObject( tissueROI, getPathClass("Centerbis") )
addObject(newTissue)
fireHierarchyUpdate()

selectObjectsByClassification("Center")
clearSelectedObjects(false)
currentClass = getPathClass("Centerbis")  
newClass = getPathClass("Center")
getAnnotationObjects().each { annotation ->  if (annotation.getPathClass().equals(currentClass)) annotation.setPathClass(newClass)}

/* 10) Clear the pollution annotation
***************************************************************************************************/
selectObjectsByClassification("pollution")
clearSelectedObjects(false)

/* 11) Detect the positive cells
***************************************************************************************************/
selectObjectsByClassification("Tissu")
runPlugin('qupath.imagej.detect.cells.PositiveCellDetection', '{"detectionImageBrightfield": "Optical density sum",  "requestedPixelSizeMicrons": 0.5,  "backgroundRadiusMicrons": 13.0,  "medianRadiusMicrons": 0.0,  "sigmaMicrons": 1.7,  "minAreaMicrons": 15.0,  "maxAreaMicrons": 200.0,  "threshold": 0.5,  "maxBackground": 0.0,  "watershedPostProcess": false,  "excludeDAB": false,  "cellExpansionMicrons": 3.0,  "includeNuclei": true,  "smoothBoundaries": true,  "makeMeasurements": true,  "thresholdCompartment": "Cell: DAB OD mean",  "thresholdPositive1": 0.05,  "thresholdPositive2": 0.4,  "thresholdPositive3": 0.6000000000000001,  "singleThreshold": true}');

selectObjectsByClassification("Inner")
runPlugin('qupath.imagej.detect.cells.PositiveCellDetection', '{"detectionImageBrightfield": "Optical density sum",  "requestedPixelSizeMicrons": 0.5,  "backgroundRadiusMicrons": 13.0,  "medianRadiusMicrons": 0.0,  "sigmaMicrons": 1.7,  "minAreaMicrons": 15.0,  "maxAreaMicrons": 200.0,  "threshold": 0.5,  "maxBackground": 0.0,  "watershedPostProcess": false,  "excludeDAB": false,  "cellExpansionMicrons": 3.0,  "includeNuclei": true,  "smoothBoundaries": true,  "makeMeasurements": true,  "thresholdCompartment": "Cell: DAB OD mean",  "thresholdPositive1": 0.05,  "thresholdPositive2": 0.4,  "thresholdPositive3": 0.6000000000000001,  "singleThreshold": true}');

selectObjectsByClassification("Center")
runPlugin('qupath.imagej.detect.cells.PositiveCellDetection', '{"detectionImageBrightfield": "Optical density sum",  "requestedPixelSizeMicrons": 0.5,  "backgroundRadiusMicrons": 13.0,  "medianRadiusMicrons": 0.0,  "sigmaMicrons": 1.7,  "minAreaMicrons": 15.0,  "maxAreaMicrons": 200.0,  "threshold": 0.5,  "maxBackground": 0.0,  "watershedPostProcess": false,  "excludeDAB": false,  "cellExpansionMicrons": 3.0,  "includeNuclei": true,  "smoothBoundaries": true,  "makeMeasurements": true,  "thresholdCompartment": "Cell: DAB OD mean",  "thresholdPositive1": 0.05,  "thresholdPositive2": 0.4,  "thresholdPositive3": 0.6000000000000001,  "singleThreshold": true}');

selectObjectsByClassification("Outer")
runPlugin('qupath.imagej.detect.cells.PositiveCellDetection', '{"detectionImageBrightfield": "Optical density sum",  "requestedPixelSizeMicrons": 0.5,  "backgroundRadiusMicrons": 13.0,  "medianRadiusMicrons": 0.0,  "sigmaMicrons": 1.7,  "minAreaMicrons": 15.0,  "maxAreaMicrons": 200.0,  "threshold": 0.5,  "maxBackground": 0.0,  "watershedPostProcess": false,  "excludeDAB": false,  "cellExpansionMicrons": 3.0,  "includeNuclei": true,  "smoothBoundaries": true,  "makeMeasurements": true,  "thresholdCompartment": "Cell: DAB OD mean",  "thresholdPositive1": 0.05,  "thresholdPositive2": 0.4,  "thresholdPositive3": 0.6000000000000001,  "singleThreshold": true}');


/* 12) Save the annotations
***************************************************************************************************/
path = buildFilePath(PROJECT_BASE_DIR, 'Measurements')
name = getProjectEntry().getImageName() + '.tsv'

//make sure the directory exists
mkdirs(path)
// Save the results
path = buildFilePath(path, name)
selectObjectsByClassification("Tissu")
saveAnnotationMeasurements(path)



