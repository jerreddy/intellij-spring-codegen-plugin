package com.sivalabs.springcodegen.action;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.LangDataKeys;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.JavaCodeStyleManager;
import com.intellij.psi.search.GlobalSearchScope;

import java.util.HashMap;
import java.util.Map;

public class SpringDataRepositoryGenerateAction extends AnAction {

    @Override
    public void actionPerformed(AnActionEvent e) {
        PsiFile psiFile = e.getDataContext().getData(LangDataKeys.PSI_FILE);
        PsiClass entityClass = getEntityClass(psiFile);
        if(entityClass == null) {
            Messages.showErrorDialog("The current class is not a JPA entity","Ooops");
            return;
        }

        String primaryKeyType = "Long";
        String toImport = null;
        PsiField idField = getIdField(entityClass);
        if(idField == null){
            Messages.showInfoMessage("The JPA entity doesn't have a field with @id","Id Field not found");
        } else {
            PsiType type = idField.getType();

            //primaryKeyType = type.getPresentableText();
            primaryKeyType = type.getCanonicalText();
            toImport = type.getCanonicalText();
        }

        JavaDirectoryService directoryService = JavaDirectoryService.getInstance();
        PsiDirectory directory = entityClass.getContainingFile().getContainingDirectory();
        String interfaceName = entityClass.getName() + "Repository";
        String repoInterfaceTemplate = "SpringDataRepo.java";

        Map<String, String> props = new HashMap<>();
        props.put("Entity",entityClass.getName());
        props.put("PrimaryKeyType",primaryKeyType);
        PsiClass generatedClass = null;
        boolean exists = directory.findFile(interfaceName+".java") != null;
        if(exists) {
            int ans = Messages.showOkCancelDialog("File already exists. Do you want to override?","File already exist", null);
            if(ans == Messages.YES) {
                directory.findFile(interfaceName+".java").delete();
                generatedClass = directoryService.createClass(directory, interfaceName, repoInterfaceTemplate, true, props);
            }
        } else {
            generatedClass = directoryService.createClass(directory,interfaceName,repoInterfaceTemplate,true, props);
        }
        Project project = entityClass.getManager().getProject();
        PsiElementFactory factory = JavaPsiFacade.getInstance(project).getElementFactory();
        final PsiClass repoInter = generatedClass;
        final String primaryKeyFQN = toImport;
        WriteCommandAction.runWriteCommandAction(project, () -> {
            //addImport(project, repoInter, primaryKeyFQN);
            shortenClassReferences(project, repoInter);
        });

    }

    private PsiField getIdField(PsiClass entityClass) {
        for (PsiField field : entityClass.getFields()) {
            if(field.hasAnnotation("javax.persistence.Id")){
                return field;
            }
        }
        return null;
    }

    private PsiClass getEntityClass(PsiFile psiFile) {
        PsiJavaFile psiJavaFile = (PsiJavaFile)psiFile;
        final PsiClass[] classes = psiJavaFile.getClasses();
        for (PsiClass aClass : classes) {
            if(aClass.hasAnnotation("javax.persistence.Entity")) {
                return aClass;
            }
        }
        return null;
    }

    private void shortenClassReferences(Project project, PsiClass psiClass) {
        final PsiFile file = psiClass.getContainingFile();
        if (!(file instanceof PsiJavaFile)) {
            return;
        }
        final PsiJavaFile javaFile = (PsiJavaFile)file;
        JavaCodeStyleManager.getInstance(project).shortenClassReferences(javaFile);
    }

    private void addImport(Project project, PsiClass psiClass, String fullyQualifiedName){
        final PsiFile file = psiClass.getContainingFile();
        if (!(file instanceof PsiJavaFile)) {
            return;
        }
        final PsiJavaFile javaFile = (PsiJavaFile)file;

        final PsiImportList importList = javaFile.getImportList();
        if (importList == null) {
            return;
        }

        // Check if already imported
        for (PsiImportStatementBase is : importList.getAllImportStatements()) {
            String impQualifiedName = is.getImportReference().getQualifiedName();
            if (fullyQualifiedName.equals(impQualifiedName)){
                return; // Already imported so nothing needed
            }
        }

        // Not imported yet so add it

        PsiClass importClass = JavaPsiFacade.getInstance(project).findClass(fullyQualifiedName, GlobalSearchScope.allScope(project));
        //PsiElementFactory elementFactory...
        //importList.add(elementFactory.createImportStatementOnDemand(fullyQualifiedName));
        //importList.add(elementFactory.createImportStatement(importClass));
        JavaCodeStyleManager.getInstance(project).addImport(javaFile,importClass);
    }
}
