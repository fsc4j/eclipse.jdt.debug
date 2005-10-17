/*******************************************************************************
 * Copyright (c) 2005 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/
package org.eclipse.jdt.debug.tests.refactoring;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.debug.core.model.IBreakpoint;
import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.IMember;
import org.eclipse.jdt.core.IMethod;
import org.eclipse.jdt.core.IPackageFragment;
import org.eclipse.jdt.core.IPackageFragmentRoot;
import org.eclipse.jdt.debug.core.IJavaMethodBreakpoint;
import org.eclipse.jdt.debug.tests.AbstractDebugTest;
import org.eclipse.jdt.internal.corext.refactoring.rename.JavaRenameProcessor;
import org.eclipse.jdt.internal.corext.refactoring.rename.RenameNonVirtualMethodProcessor;
import org.eclipse.ltk.core.refactoring.CreateChangeOperation;
import org.eclipse.ltk.core.refactoring.PerformChangeOperation;
import org.eclipse.ltk.core.refactoring.Refactoring;
import org.eclipse.ltk.core.refactoring.RefactoringStatus;
import org.eclipse.ltk.core.refactoring.participants.RenameRefactoring;

public class RenameMethodUnitTests extends AbstractDebugTest{

	public RenameMethodUnitTests(String name) {
		super(name);
	}

	protected void cleanTestFiles() throws Exception
	{
		//ensure proper packages
		//cleanup new Package
		IPackageFragmentRoot root = getPackageFragmentRoot(getJavaProject(), "src");
		IPackageFragment fragment = root.getPackageFragment("renamedPackage");
		if(fragment.exists())
			fragment.delete(true, new NullProgressMonitor());	
		
		fragment = root.getPackageFragment("a.b.c");
		if(!fragment.exists())
			root.createPackageFragment("a.b.c", true, new NullProgressMonitor());
		
				
		//cleanup Movee
		IFile target = getJavaProject().getProject().getFile("src/a/b/Movee.java");//move up a dir
		if(target.exists())
			target.delete(false, false, null);		
		target = getJavaProject().getProject().getFile("src/a/b/c/Movee.java");//move up a dir
		if(target.exists())
			target.delete(false, false, null);
		//get original source & replace old result
		IFile source = getJavaProject().getProject().getFile("src/a/MoveeSource");//no .java - it's a bin
		source.copy(target.getFullPath(), false, null );
		
		//cleanup child
		target = getJavaProject().getProject().getFile("src/a/b/c/MoveeChild.java");//move up a dir
		if(target.exists())
			target.delete(false, false, null);
		//get original source & replace old result
		source = getJavaProject().getProject().getFile("src/a/MoveeChildSource");//no .java - it's a bin
		source.copy(target.getFullPath(), false, null );
	}

	protected final void performRefactor(final Refactoring refactoring) throws Exception {
		if(refactoring==null)
			return;
		CreateChangeOperation create= new CreateChangeOperation(refactoring);
		refactoring.checkFinalConditions(new NullProgressMonitor());
		PerformChangeOperation perform= new PerformChangeOperation(create);
		try {
			ResourcesPlugin.getWorkspace().run(perform, new NullProgressMonitor());//maybe SubPM?
		} catch (NullPointerException e) { e.printStackTrace(); }
		waitForBuild();
	}
	
	/**
	 * @param src
	 * @param pack
	 * @param cunit
	 * @param fullTargetName
	 * @param targetLineage
	 * @throws Exception
	 */
	protected void runMethodBreakpointTest(String src, String pack, String cunit, String fullTargetName, String targetLineage) throws Exception {
		cleanTestFiles();
		String newMethodName = "renamedMethod";
		try {
			//create breakpoint to test
			IJavaMethodBreakpoint breakpoint = createMethodBreakpoint(src, pack, cunit,fullTargetName, true, false);
			//refactor
			Refactoring ref = setupRefactor(src, pack, cunit, fullTargetName);
			performRefactor(ref);
			//test breakpoints
			IBreakpoint[] breakpoints = getBreakpointManager().getBreakpoints();
			assertEquals("wrong number of breakpoints", 1, breakpoints.length);
			breakpoint = (IJavaMethodBreakpoint) breakpoints[0];
			assertTrue("Breakpoint Marker has ceased existing",breakpoint.getMarker().exists());
			assertEquals("wrong type name", targetLineage, breakpoint.getTypeName());
			assertEquals("breakpoint attached to wrong method",newMethodName,breakpoint.getMethodName());
		} catch (Exception e) {
			throw e;
		} finally {
			removeAllBreakpoints();
		}
	}

	/**
	 * Will setup the refactoring for Methods and members declared within 
	 * members (i.e. members of anonymous classes)
	 * @param root
	 * @param packageName
	 * @param cuName
	 * @param fullName
	 * @return
	 * @throws Exception
	 */
	protected Refactoring setupRefactor(String root, String packageName, String cuName, String fullName) throws Exception {
		
		IJavaProject javaProject = getJavaProject();
		ICompilationUnit cunit = getCompilationUnit(javaProject, root, packageName, cuName);
		IMember member = getMember(cunit, fullName);
		IMethod method = (IMethod)member;
		
		IPackageFragment packageFragment = (IPackageFragment )cunit.getParent();
		JavaRenameProcessor proc = new RenameNonVirtualMethodProcessor(method);
		proc.setNewElementName("renamedMethod");
		
		RenameRefactoring ref= new RenameRefactoring(proc);
		
		//setup final refactoring conditions
		RefactoringStatus refactoringStatus= ref.checkAllConditions(new NullProgressMonitor());
		if(!refactoringStatus.isOK())
		{
			System.out.println(refactoringStatus.getMessageMatchingSeverity(refactoringStatus.getSeverity()));
			return null;
		}		
		
		return ref;
	}

	public void testInnerAnonymousTypeMethodBreakpoint() throws Exception {
			String 	src = "src", 
					pack = "a.b.c",
					cunit = "MoveeChild.java",
					fullTargetName = "MoveeChild$InnerChildType$innerChildsMethod()V$1$anonTypeMethod()V",
					targetLineage = pack+"."+"MoveeChild$InnerChildType$1";
			runMethodBreakpointTest(src, pack, cunit, fullTargetName, targetLineage);
	}//end testBreakPoint

	public void testInnerMethodBreakpoint() throws Exception {
			String 	src = "src", 
					pack = "a.b.c",
					cunit = "MoveeChild.java",
					fullTargetName = "MoveeChild$InnerChildType$innerChildsMethod()V",
					targetLineage = pack+"."+"MoveeChild$InnerChildType";
			runMethodBreakpointTest(src, pack, cunit, fullTargetName, targetLineage);
	}//end testBreakPoint	

	public void testNonPublicAnonymousTypeMethodBreakpoint() throws Exception {
			String 	src = "src", 
					pack = "a.b.c",
					cunit = "MoveeChild.java",
					fullTargetName = "NonPublicChildType$nonPublicChildsMethod()V$1$anonTypeMethod()V",
					targetLineage = pack+"."+"NonPublicChildType$1";
			runMethodBreakpointTest(src, pack, cunit, fullTargetName, targetLineage);
	}//end testBreakPoint		

	public void testNonPublicMethodBreakpoint() throws Exception {
			String 	src = "src", 
					pack = "a.b.c",
					cunit = "MoveeChild.java",
					fullTargetName = "NonPublicChildType$nonPublicChildsMethod()V$",
					targetLineage = pack+"."+"NonPublicChildType";
			runMethodBreakpointTest(src, pack, cunit, fullTargetName, targetLineage);
	}//end testBreakPoint
	
	public void testPublicAnonymousTypeMethodBreakpoint() throws Exception {
		String 	src = "src", 
		pack = "a.b.c",
		cunit = "MoveeChild.java",
		fullTargetName = "MoveeChild$childsMethod()V$1$anonTypeMethod()V",
		targetLineage = pack+"."+"MoveeChild$1";
		
		runMethodBreakpointTest(src, pack, cunit, fullTargetName, targetLineage);
	}//end testBreakPoint

	public void testPublicMethodBreakpoint() throws Exception {
			String 	src = "src", 
					pack = "a.b.c",
					cunit = "MoveeChild.java",
					fullTargetName = "MoveeChild$childsMethod()V",
					targetLineage = pack+"."+"MoveeChild";
			runMethodBreakpointTest(src, pack, cunit, fullTargetName, targetLineage);
	}//end testBreakPoint		
	
}