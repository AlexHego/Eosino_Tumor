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
 3b) Simplify Tissu annotation
 3c) Simplify Tumor annotation
 4) Script to help with annotating tumor regions, separating the tumor margin from the center. 
 5) 5) remove annotation Tissu and Tumor
 6) Detect the pollution particles
 7) remove the area of pollution from the area of annotation Outer
 8) remove the area of pollution from the area of annotation Inner
 9) remove the area of pollution from the area of annotation Center
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
import qupath.lib.roi.ShapeSimplifier
double altitudeThreshold = 10.0 // Higher value results in simpler polygons/fewer vertices

// Set the model of random forest to detect anthracose
model_anthracose = "anthracose"

// How much to expand each region
double expandMarginMicrons = 1200

// Define the colors
def colorInnerMargin = getColorRGB(0, 0, 200)
def colorOuterMargin = getColorRGB(0, 200, 0)
def colorCentral = getColorRGB(200, 0, 0)

/* 1)  Set the vectors stain (if needed)
****************************************************/
//setColorDeconvolutionStains('{"Name" : "H-DAB estimated", "Stain 1" : "Hematoxylin", "Values 1" : "0.66034 0.69836 0.27614", "Stain 2" : "DAB", "Values 2" : "0.50613 0.58015 0.63817", "Background" : " 187 182 174"}');


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



/*3bis Simplify Tissu annotation
 * ***************************************************************************************************/

selectObjectsByClassification("Tissu")
complexAnno = getSelectedObjects()
simplifiedAnno = complexAnno.collect{
    simpleRoi = ShapeSimplifier.simplifyShape(it.getROI(), altitudeThreshold)
    simpleAnno = PathObjects.createAnnotationObject(simpleRoi, getPathClass("Simplified Annotation"))
    return simpleAnno
}
addObjects(simplifiedAnno)

selectObjectsByClassification("Tissu")
clearSelectedObjects(false)
currentClass = getPathClass("Simplified Annotation")  
newClass = getPathClass("Tissu")
getAnnotationObjects().each { annotation ->  if (annotation.getPathClass().equals(currentClass)) annotation.setPathClass(newClass)}


/*3ter Simplify Tumor annotation
 * ***************************************************************************************************/

selectObjectsByClassification("Tumor")
complexAnno = getSelectedObjects()

simplifiedAnno = complexAnno.collect{
    simpleRoi = ShapeSimplifier.simplifyShape(it.getROI(), altitudeThreshold)
    simpleAnno = PathObjects.createAnnotationObject(simpleRoi, getPathClass("Simplified Annotation"))
    return simpleAnno
}
addObjects(simplifiedAnno)

selectObjectsByClassification("Tumor")
clearSelectedObjects(false)
currentClass = getPathClass("Simplified Annotation")  
newClass = getPathClass("Tumor")
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

// Calculate how much to expand the middle layer
double expandPixels = expandMarginMicrons / cal.getAveragedPixelSizeMicrons()
def areaTumor = roiOriginal.getGeometry()

// Get the outer margin area
def geomInner = areaTumor.buffer(expandPixels)
geomInner = geomInner.difference(areaTumor)
geomInner = geomInner.intersection(areaTissue)
def roiInner = GeometryTools.geometryToROI(geomInner, plane)
def annotationInner = PathObjects.createAnnotationObject(roiInner, getPathClass("Inner"))
annotationInner.setName("Inner margin")
annotationInner.setColorRGB(colorInnerMargin)
addObject(annotationInner)

// Calculate how much to expand the outer layer
double expandPixels2 = expandMarginMicrons*2 / cal.getAveragedPixelSizeMicrons()

// Get the outer margin area
def geomOuter = areaTumor.buffer(expandPixels2)
geomOuter = geomOuter.difference(geomInner)
geomOuter = geomOuter.difference(areaTumor)
geomOuter = geomOuter.intersection(areaTissue)
def roiOuter = GeometryTools.geometryToROI(geomOuter, plane)
// def annotationOuter = PathObjects.createAnnotationObject(roiOuter)
def annotationOuter = PathObjects.createAnnotationObject(roiOuter, getPathClass("Outer"))
annotationOuter.setName("Outer margin")
annotationOuter.setColorRGB(colorOuterMargin)
addObject(annotationOuter)

// Get the central area
geomCentral = areaTumor.intersection(areaTissue)
def roiCentral = GeometryTools.geometryToROI(geomCentral, plane)
// change def annotationCentral = PathObjects.createAnnotationObject(roiCentral)
def annotationCentral = PathObjects.createAnnotationObject(roiCentral, getPathClass("Center"))
annotationCentral.setName("Center")
annotationCentral.setColorRGB(colorCentral)
addObject(annotationCentral)

/* 5) remove annotation Tissu and Tumor
***************************************************************************************************/

selectObjectsByClassification("Tissu")
clearSelectedObjects(false)

selectObjectsByClassification("Tumor")
clearSelectedObjects(false)


/* 6) Detect the pollution particles 
***************************************************************************************************/
rois = getAnnotationObjects().collect{it.getROI()}
newAnnotations = []

rois.each{
    newAnnotations << PathObjects.createAnnotationObject(it, getPathClass("duplicateAnnotation"))
}

addObjects(newAnnotations)

selectObjectsByClassification("duplicateAnnotation");
mergeSelectedAnnotations();

selectObjectsByClassification("duplicateAnnotation");
createAnnotationsFromPixelClassifier(model_anthracose, 1.0, 5.0)


selectObjectsByClassification("duplicateAnnotation")
clearSelectedObjects(true)

////////////////////////////////////////////////////////////////////////////
// reclaim memory from the classifier
javafx.application.Platform.runLater {
getCurrentViewer().getImageRegionStore().cache.clear()
System.gc()
}
Thread.sleep(10)

/////////////////////////////////////////////////////////////////////////////


/* 7) remove the area of pollution from the area of annotation Outer
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
newTissue = PathObjects.createAnnotationObject( tissueROI, getPathClass("Outer_external") )
addObject(newTissue)
fireHierarchyUpdate()

selectObjectsByClassification("Outer")
clearSelectedObjects(false)

/* 8) remove the area of pollution from the area of annotation Inner
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
newTissue = PathObjects.createAnnotationObject( tissueROI, getPathClass("Outer_internal") )
addObject(newTissue)
fireHierarchyUpdate()

selectObjectsByClassification("Inner")
clearSelectedObjects(false)

/* 9) remove the area of pollution from the area of annotation Center
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
newTissue = PathObjects.createAnnotationObject( tissueROI, getPathClass("Tumor") )
addObject(newTissue)
fireHierarchyUpdate()

selectObjectsByClassification("Center")
clearSelectedObjects(false)

/* 10) Clear the pollution annotation
***************************************************************************************************/
selectObjectsByClassification("pollution")
clearSelectedObjects(false)

/* 11) Detect the positive cells
***************************************************************************************************/
selectObjectsByClassification("Outer_external")
runPlugin('qupath.imagej.detect.cells.PositiveCellDetection', '{"detectionImageBrightfield": "Optical density sum",  "requestedPixelSizeMicrons": 0.5,  "backgroundRadiusMicrons": 13.0,  "medianRadiusMicrons": 0.0,  "sigmaMicrons": 1.7,  "minAreaMicrons": 15.0,  "maxAreaMicrons": 315.0,  "threshold": 0.3,  "maxBackground": 0.0,  "watershedPostProcess": false,  "excludeDAB": false,  "cellExpansionMicrons": 3.0,  "includeNuclei": true,  "smoothBoundaries": true,  "makeMeasurements": true,  "thresholdCompartment": "Cell: DAB OD mean",  "thresholdPositive1": 0.04,  "thresholdPositive2": 0.1,  "thresholdPositive3": 0.3,  "singleThreshold": false}');

selectObjectsByClassification("Outer_internal")
runPlugin('qupath.imagej.detect.cells.PositiveCellDetection', '{"detectionImageBrightfield": "Optical density sum",  "requestedPixelSizeMicrons": 0.5,  "backgroundRadiusMicrons": 13.0,  "medianRadiusMicrons": 0.0,  "sigmaMicrons": 1.7,  "minAreaMicrons": 15.0,  "maxAreaMicrons": 315.0,  "threshold": 0.3,  "maxBackground": 0.0,  "watershedPostProcess": false,  "excludeDAB": false,  "cellExpansionMicrons": 3.0,  "includeNuclei": true,  "smoothBoundaries": true,  "makeMeasurements": true,  "thresholdCompartment": "Cell: DAB OD mean",  "thresholdPositive1": 0.04,  "thresholdPositive2": 0.1,  "thresholdPositive3": 0.3,  "singleThreshold": false}');

selectObjectsByClassification("Tumor")
runPlugin('qupath.imagej.detect.cells.PositiveCellDetection', '{"detectionImageBrightfield": "Optical density sum",  "requestedPixelSizeMicrons": 0.5,  "backgroundRadiusMicrons": 13.0,  "medianRadiusMicrons": 0.0,  "sigmaMicrons": 1.7,  "minAreaMicrons": 15.0,  "maxAreaMicrons": 315.0,  "threshold": 0.3,  "maxBackground": 0.0,  "watershedPostProcess": false,  "excludeDAB": false,  "cellExpansionMicrons": 3.0,  "includeNuclei": true,  "smoothBoundaries": true,  "makeMeasurements": true,  "thresholdCompartment": "Cell: DAB OD mean",  "thresholdPositive1": 0.04,  "thresholdPositive2": 0.1,  "thresholdPositive3": 0.3,  "singleThreshold": false}');




/* 12) Save the annotations
***************************************************************************************************/
path = buildFilePath(PROJECT_BASE_DIR, 'Measurements')

name = getProjectEntry().getImageName() + '.tsv'


//make sure the directory exists
mkdirs(path)

// Save the results
path = buildFilePath(path, name)
selectObjectsByClassification("Tumor")
saveAnnotationMeasurements(path)

