int main() {
	printInt(new BT().Start());
    	return 0;
}


// This class invokes the methods to create a tree,
// insert, delete and serach for  elements on it
class BT {

    int Start(){
	Tree root ;
	boolean ntb ;
	int nti ;

	root = new Tree();
	ntb = root.Init(16);
	ntb = root.Print();
	printInt(100000000);
	ntb = root.Insert(8) ;
	ntb = root.Print();
	ntb = root.Insert(24) ;
	ntb = root.Insert(4) ;
	ntb = root.Insert(12) ;
	ntb = root.Insert(20) ;
	ntb = root.Insert(28) ;
	ntb = root.Insert(14) ;
	ntb = root.Print();
	printInt(root.Search(24));
	printInt(root.Search(12));
	printInt(root.Search(16));
	printInt(root.Search(50));
	printInt(root.Search(12));
	ntb = root.Delete(12);
	ntb = root.Print();
	printInt(root.Search(12));

	return 0 ;
    }

}

class Tree{
    Tree left ;
    Tree right;
    int key ;
    boolean has_left ;
    boolean has_right ;
    Tree my_null ;

    // Initialize a node with a key value and no children
    boolean Init(int v_key){
	key = v_key ;
	has_left = false ;
	has_right = false ;
	return true ;
    }

    // Update the right child with rn
    boolean SetRight(Tree rn){
	right = rn ;
	return true ;
    }
    
    // Update the left child with ln
    boolean SetLeft(Tree ln){
	left = ln ;
	return true ;
    }

    Tree GetRight(){
	return right ;
    }

    Tree GetLeft(){
	return left;
    }

    int GetKey(){
	return key ;
    }

    boolean SetKey(int v_key){
	key = v_key ;
	return true ;
    }

    boolean GetHas_Right(){
	return has_right ;
    }

    boolean GetHas_Left(){
	return has_left ;
    }

    boolean SetHas_Left(boolean val){
	 has_left = val ;
	 return true ;
    }

    boolean SetHas_Right(boolean val){
	 has_right = val ;
	 return true ;
    }
    
    // This method compares two integers and
    // returns true if they are equal and false
    // otherwise
    boolean Compare(int num1 , int num2){
	boolean ntb ;
	int nti ;

	ntb = false ;
	nti = num2 + 1 ;
	if (num1 < num2) ntb = false ;
	else if (!(num1 < nti)) ntb = false ;
	else ntb = true ;
	return ntb ;
    }


    // Insert a new element in the tree
    boolean Insert(int v_key){
	Tree new_node ;
	boolean ntb ;
	boolean cont ;
	int key_aux ;
	Tree current_node ;

	new_node = new Tree();
	ntb = new_node.Init(v_key) ;
	current_node = this ;
	cont = true ;
	while (cont){
	    key_aux = current_node.GetKey();
	    if (v_key < key_aux){
		if (current_node.GetHas_Left())
		    current_node = current_node.GetLeft() ;
		else {
		    cont = false ;
		    ntb = current_node.SetHas_Left(true);
		    ntb = current_node.SetLeft(new_node);
		}
	    }
	    else{
		if (current_node.GetHas_Right())
		    current_node = current_node.GetRight() ;
		else {
		    cont = false ;
		    ntb = current_node.SetHas_Right(true);
		    ntb = current_node.SetRight(new_node);
		}
	    }
	}
	return true ;
    }


    // Delete an element from the tree
    boolean Delete(int v_key){
	Tree current_node ;
	Tree parent_node ;
	boolean cont ;
	boolean found ;
	boolean is_root ;
	int key_aux ;
	boolean ntb ;

	current_node = this ;
	parent_node = this ;
	cont = true ;
	found = false ;
	is_root = true ;
	while (cont){
	    key_aux = current_node.GetKey();
	    if (v_key < key_aux)
		if (current_node.GetHas_Left()){
		    parent_node = current_node ;
		    current_node = current_node.GetLeft() ;
		}
		else cont = false ;
	    else 
		if (key_aux < v_key)
		    if (current_node.GetHas_Right()){
			parent_node = current_node ;
			current_node = current_node.GetRight() ;
		    }
		    else cont = false ;
		else { 
		    if (is_root) 
			if ((!current_node.GetHas_Right()) && 
			    (!current_node.GetHas_Left()) )
			    ntb = true ;
			else 
			    ntb = this.Remove(parent_node,current_node); 
		    else ntb = this.Remove(parent_node,current_node);
		    found = true ;
		    cont = false ;
		}
	    is_root = false ;
	}
	return found ;
    }


    // Check if the element to be removed will use the
    // righ or left subtree if one exists
    boolean Remove(Tree p_node, Tree c_node){
	boolean ntb ;
	int auxkey1 ;
	int auxkey2 ;
	
	if (c_node.GetHas_Left()) 
	    ntb = this.RemoveLeft(p_node,c_node) ;
	else 
	    if (c_node.GetHas_Right())
		ntb = this.RemoveRight(p_node,c_node) ;
	    else {
		auxkey1 = c_node.GetKey();
		//auxtree01 = p_node.GetLeft() ;
		//auxkey2 = auxtree01.GetKey() ;
		auxkey2 = (p_node.GetLeft()).GetKey() ;
		if (this.Compare(auxkey1,auxkey2)) {
		    ntb = p_node.SetLeft(my_null);
		    ntb = p_node.SetHas_Left(false);
		}
		else {
		    ntb = p_node.SetRight(my_null);
		    ntb = p_node.SetHas_Right(false);
		}
	    }
	return true ;
    }


    // Copy the child key to the parent until a leaf is
    // found and remove the leaf. This is done with the
    // right subtree
    boolean RemoveRight(Tree p_node, Tree c_node){
	boolean ntb ;

	while (c_node.GetHas_Right()){
	    //auxtree01 = c_node.GetRight() ;
	    //auxint02 = auxtree01.GetKey();
	    //ntb = c_node.SetKey(auxint02);
	    ntb = c_node.SetKey((c_node.GetRight()).GetKey());
	    p_node = c_node ;
	    c_node = c_node.GetRight() ;
	}
	ntb = p_node.SetRight(my_null);
	ntb = p_node.SetHas_Right(false);
	return true ;
    }


    // Copy the child key to the parent until a leaf is
    // found and remove the leaf. This is done with the
    // left subtree
    boolean RemoveLeft(Tree p_node, Tree c_node){
	boolean ntb ;

	while (c_node.GetHas_Left()){
	    //auxtree01 = c_node.GetLeft() ;
	    //auxint02 = auxtree01.GetKey();
	    //ntb = c_node.SetKey(auxint02);
	    ntb = c_node.SetKey((c_node.GetLeft()).GetKey());
	    p_node = c_node ;
	    c_node = c_node.GetLeft() ;
	}
	ntb = p_node.SetLeft(my_null);
	ntb = p_node.SetHas_Left(false);
	return true ;
    }

    // Search for an elemnt in the tree
    int Search(int v_key){
	boolean cont ;
	int ifound ;
	Tree current_node;
	int key_aux ;

	current_node = this ;
	cont = true ;
	ifound = 0 ;
	while (cont){
	    key_aux = current_node.GetKey();
	    if (v_key < key_aux)
		if (current_node.GetHas_Left())
		    current_node = current_node.GetLeft() ;
		else cont = false ;
	    else 
		if (key_aux < v_key)
		    if (current_node.GetHas_Right())
			current_node = current_node.GetRight() ;
		    else cont = false ;
		else { 
		    ifound = 1 ;
		    cont = false ;
		}
	}
	return ifound ;
    }

    // Invoke the method to really print the tree elements
    boolean Print(){
	Tree current_node;
	boolean ntb ;

	current_node = this ;
	ntb = this.RecPrint(current_node);
	return true ;
    }

    // Print the elements of the tree
    boolean RecPrint(Tree node){
	boolean ntb ;

	if (node.GetHas_Left()){
	    //auxtree01 = node.GetLeft() ;
	    //ntb = this.RecPrint(auxtree01);
	    ntb = this.RecPrint(node.GetLeft());
	} else ntb = true ;
	printInt(node.GetKey());
	if (node.GetHas_Right()){
	    //auxtree01 = node.GetRight() ;
	    //ntb = this.RecPrint(auxtree01);
	    ntb = this.RecPrint(node.GetRight());
	} else ntb = true ;
	return true ;
    }

}
   