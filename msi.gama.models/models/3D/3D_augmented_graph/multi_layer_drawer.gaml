model move   

global {
	int layerSize parameter: 'Layer size' min: 10 <- 50 ;
	file image1 <- file('images/simple_graph.png') ;
	file image2 <- file('images/simple_graph_colored.png') ;
	file image3 <- file('images/simple_graph_clustered.png') ;
	
	string label1 <-"Simple graph";
	string label2 <-"Simple graph colored";
	string label3 <-"Simple graph clustered";
	
	int nb_layer<-0;
	list images of: file;
	list labels of:string;

	init { 		
		add image1 to: images; 
		add image2 to: images;  
		add image3 to: images;
		
		add label1 to: labels;
		add label2 to: labels;
		add label3 to: labels; 
		 
		create ImageAgent number:3{
			set location <-{0,0};
			set image<-images at nb_layer;
			set label<-labels at nb_layer;
			set shape <- shape add_z(layerSize*nb_layer);
			set nb_layer <- nb_layer+1;			
		}
			
	}  
} 
 
environment;  
 
entities { 
	species ImageAgent skills: [moving]{
	  file image;
	  string label;		
	  aspect image{
		draw image:image.path  size : layerSize;
		draw text:label;
	  }
	}
}
experiment display  type: gui {
	parameter 'Image 1:' var: image1 category: 'Raster' ;
	parameter 'Image 2:' var: image2 category: 'Raster' ;
	parameter 'Image 3:' var: image3 category: 'Raster' ;
	
	parameter 'Label 1:' var: label1 category: 'String' ;
	parameter 'Label 2:' var: label2 category: 'String' ;
	parameter 'Label 3:' var: label3 category: 'String' ;
	
	
	output {
		display Display refresh_every: 1 type:opengl{
			species ImageAgent aspect:image;
		}
	}
}
