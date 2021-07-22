package com.project.methodfqnresolver;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.BufferedReader;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStreamReader;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import java.time.Instant;
import java.util.*;
import java.util.stream.Stream;
import java.util.stream.Collectors;

import com.github.javaparser.ast.expr.Expression;
import com.github.kevinsawicki.http.HttpRequest;
import com.project.methodfqnresolver.model.MavenPackage;
import com.project.methodfqnresolver.model.Query;
import com.project.methodfqnresolver.model.ResolvedFile;
import com.project.methodfqnresolver.model.SynchronizedTypeSolver;

import com.project.methodfqnresolver.utils.QueriesUtil;
import com.project.methodfqnresolver.utils.FileUtil;

import org.json.JSONArray;
import org.json.JSONObject;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ParseProblemException;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.stmt.BlockStmt;

import com.github.javaparser.ast.expr.Name;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.resolution.UnsolvedSymbolException;
import com.github.javaparser.resolution.declarations.ResolvedMethodDeclaration;
import com.github.javaparser.resolution.types.ResolvedType;
import com.github.javaparser.symbolsolver.JavaSymbolSolver;
import com.github.javaparser.symbolsolver.model.resolution.TypeSolver;
import com.github.javaparser.symbolsolver.resolution.typesolvers.CombinedTypeSolver;
import com.github.javaparser.symbolsolver.resolution.typesolvers.JarTypeSolver;
import com.github.javaparser.symbolsolver.resolution.typesolvers.JavaParserTypeSolver;
import com.github.javaparser.symbolsolver.resolution.typesolvers.ReflectionTypeSolver;

public class App {
    // run multiple token
    // please make sure that the number of thread is equal with the number of tokens
    private static final int NUMBER_THREADS = 3;
    private static final int NUMBER_CORE = 1;

    // parameter for the request
    private static final String PARAM_QUERY = "q"; //$NON-NLS-1$
    private static final String PARAM_PAGE = "page"; //$NON-NLS-1$
    private static final String PARAM_PER_PAGE = "per_page"; //$NON-NLS-1$

    // links from the response header
    private static final String META_REL = "rel"; //$NON-NLS-1$
    private static final String META_NEXT = "next"; //$NON-NLS-1$
    private static final String DELIM_LINKS = ","; //$NON-NLS-1$
    private static final String DELIM_LINK_PARAM = ";"; //$NON-NLS-1$

    // response code from github
    private static final int BAD_CREDENTIAL = 401;
    private static final int RESPONSE_OK = 200;
    private static final int ABUSE_RATE_LIMITS = 403;
    private static final int UNPROCESSABLE_ENTITY = 422;

    // number of needed file to be resolved
    private static final int MAX_RESULT = 1;

    // folder location to save the downloaded files and jars
    private static String PROJ_DIR = "src/main/java/com/project/methodfqnresolver/";
//    private static String CODE_DIR = PROJ_DIR + "codes/";
    private static String CODE_DIR = PROJ_DIR + "testCodes/";
    private static String SNIPPET_OUT_DIR = PROJ_DIR + "codeSnipppets/";

    private static String METHOD_OUT_DIR = PROJ_DIR + "outputs/";
    private static String DATA_LOCATION = PROJ_DIR + "data/";
    private static final String JARS_LOCATION = PROJ_DIR +"jars/";


    private static final String endpoint = "https://api.github.com/search/code";
//    private static final Integer codeLineVaryingRange = 8;
//    private static SynchronizedData synchronizedData = new SynchronizedData();
//    private static SynchronizedFeeder synchronizedFeeder = new SynchronizedFeeder();
//    private static ResolvedData resolvedData = new ResolvedData();
    private static SynchronizedTypeSolver synchronizedTypeSolver = new SynchronizedTypeSolver();

    private static Instant start;
    private static Instant currentTime;


    public static ArrayList<Path> getFilesFromFolder(String folder) throws IOException{
        List<Path> paths;
        try (Stream<Path> walkingPaths = Files.walk(Paths.get(folder))) {
            paths = walkingPaths.filter(Files::isRegularFile).collect(Collectors.toList());
        }
        ArrayList<Path> listPaths = new ArrayList<Path>(paths);
        return listPaths;
    }

    private static String downloadMavenJar(String groupId, String artifactId) {
        String path = JARS_LOCATION + artifactId + "-latest.jar";
        String url = "http://repository.sonatype.org/service/local/artifact/maven/redirect?r=central-proxy&g=" + groupId
                + "&a=" + artifactId + "&v=LATEST";
         System.out.println("Download jar from URL: " + url);
        File jarFile = new File(path);

        if (!jarFile.exists()) {
            // Equivalent command conversion for Java execution
            String[] command = { "curl", "-L", url, "-o", path };

            ProcessBuilder process = new ProcessBuilder(command);
            Process p;
            try {
                p = process.start();
                BufferedReader reader = new BufferedReader(new InputStreamReader(p.getInputStream()));
                StringBuilder builder = new StringBuilder();
                String line = null;
                while ((line = reader.readLine()) != null) {
                    builder.append(line);
                    builder.append(System.getProperty("line.separator"));
                }
                String result = builder.toString();
                System.out.print(result);

            } catch (IOException e) {
                System.out.print("error");
                e.printStackTrace();
            }
        }

        return path;

    }

    private static MavenPackage getMavenPackageArtifact(String qualifiedName) {

        MavenPackage mavenPackageName = new MavenPackage();

        String url = "https://search.maven.org/solrsearch/select?q=fc:" + qualifiedName + "&wt=json";

        HttpRequest request = HttpRequest.get(url, false);

        // handle response
        int responseCode = request.code();
        if (responseCode == RESPONSE_OK) {
            JSONObject body = new JSONObject(request.body());
            JSONObject response = body.getJSONObject("response");
            int numFound = response.getInt("numFound");
            JSONArray mavenPackages = response.getJSONArray("docs");
            if (numFound > 0) {
                mavenPackageName.setId(mavenPackages.getJSONObject(0).getString("id")); // set the id
                mavenPackageName.setGroupId(mavenPackages.getJSONObject(0).getString("g")); // set the first group id
                mavenPackageName.setArtifactId(mavenPackages.getJSONObject(0).getString("a")); // set the first artifact
                // id
                mavenPackageName.setVersion(mavenPackages.getJSONObject(0).getString("v")); // set the first version id
            }
        } else {
            System.out.println("Response Code: " + responseCode);
            System.out.println("Response Body: " + request.body());
            System.out.println("Response Headers: " + request.headers());
        }

        return mavenPackageName;
    }

    private static List<String> getNeededJars(File file) {
        List<String> jarsPath = new ArrayList<String>();
        TypeSolver typeSolver = new CombinedTypeSolver(new ReflectionTypeSolver(false),
                new JavaParserTypeSolver(new File("src/")));
        StaticJavaParser.getConfiguration().setSymbolResolver(new JavaSymbolSolver(typeSolver));

        // list of specific package imported
        List<String> importedPackages = new ArrayList<String>();
        try {
            CompilationUnit cu = StaticJavaParser.parse(file);
            cu.findAll(Name.class).forEach(mce -> {
                String[] names = mce.toString().split("[.]");
                if (names.length >= 2) { // filter some wrong detected import like Override, SupressWarning
                    if (importedPackages.isEmpty()) {
                        importedPackages.add(mce.toString());
                    } else {
                        boolean isAlreadyDefined = false;
                        for (int i = 0; i < importedPackages.size(); i++) {
                            if (importedPackages.get(i).contains(mce.toString())) {
                                isAlreadyDefined = true;
                                break;
                            }
                        }
                        if (!isAlreadyDefined) {
                            importedPackages.add(mce.toString());
                        }
                    }
                }
            });
        } catch (FileNotFoundException e) {
            System.out.println("EXCEPTION");
            System.out.println("File not found!");
        } catch (ParseProblemException parseException) {
            return jarsPath;
        }
        // Print out importedPackages
//        System.out.println("[importedPackages]");
//        for (int i=0; i < importedPackages.size(); i++) {
//            System.out.println(importedPackages.get(i));
//        }
        // System.out.println();
        // System.out.println("=== Imported Packages ==");
        // for (int i = 0; i < importedPackages.size(); i++) {
        // System.out.println(importedPackages.get(i));
        // }

        // filter importedPackages
        // remove the project package and java predefined package
        List<String> neededPackages = new ArrayList<String>();
        if (importedPackages.size() > 0) {
            String qualifiedName = importedPackages.get(0);
            String[] names = qualifiedName.split("[.]");
            String projectPackage = names[0].toString();
            for (int i = 1; i < importedPackages.size(); i++) { // the first package is skipped
                qualifiedName = importedPackages.get(i);
                names = qualifiedName.split("[.]");
                String basePackage = names[0];
                if (!basePackage.equals("java") && !basePackage.equals("javax") && !basePackage.equals("Override")) {
                    neededPackages.add(importedPackages.get(i));
                }
            }
        }

         System.out.println("=== Needed Packages ==");
         for (int i = 0; i < neededPackages.size(); i++) {
            System.out.println(neededPackages.get(i));
         }

        List<MavenPackage> mavenPackages = new ArrayList<MavenPackage>();

        // get the groupId and artifactId from the package qualified name
        for (int i = 0; i < neededPackages.size(); i++) {
            String qualifiedName = neededPackages.get(i);
            MavenPackage mavenPackage = getMavenPackageArtifact(qualifiedName);

            if (!mavenPackage.getId().equals("")) { // handle if the maven package is not exist
                // filter if the package is used before
                boolean isAlreadyUsed = false;
                for (int j = 0; j < mavenPackages.size(); j++) {
                    MavenPackage usedPackage = mavenPackages.get(j);
                    if (mavenPackage.getGroupId().equals(usedPackage.getGroupId())
                            && mavenPackage.getArtifactId().equals(usedPackage.getArtifactId())) {
                        isAlreadyUsed = true;
                    }
                }
                if (!isAlreadyUsed) {
                    mavenPackages.add(mavenPackage);
                }
            }
        }

        // System.out.println();
        // System.out.println("=== Maven Packages ==");
        // for (int i = 0; i < mavenPackages.size(); i++) {
        // System.out.println("GroupID: " + mavenPackages.get(i).getGroupId() + " -
        // ArtifactID: "
        // + mavenPackages.get(i).getArtifactId());
        // }

        System.out.println("=== Downloading Packages ==");
        for (int i = 0; i < mavenPackages.size(); i++) {
            String pathToJar = downloadMavenJar(mavenPackages.get(i).getGroupId(),
                    mavenPackages.get(i).getArtifactId());
            if (!pathToJar.equals("")) {
                 System.out.println("Downloaded: " + pathToJar);
                jarsPath.add(pathToJar);
            }
        }

        return jarsPath;
    }
    private static ArrayList<String> getSnippetCode(String pathFile, ArrayList<Integer> lines) {
        ArrayList<String> codes = new ArrayList<String>();

        int min, max, length;
        length = lines.size();
        if (length == 1) {
            min = max = lines.get(0).intValue();
        } else {
            min = lines.get(0).intValue();
            max = lines.get(0).intValue();
            for (int i = 1; i < length; i++) {
                if ( lines.get(i).intValue() < min) {
                    min = lines.get(i).intValue();
                }
                if ( lines.get(i).intValue() > max) {
                    max = lines.get(i).intValue();
                }
            }
        }

        BufferedReader reader;
        int i = 0;
        try {
            reader = new BufferedReader(new FileReader(pathFile));
            String line = reader.readLine();
            while (line != null) {
                i++;
                // System.out.println(line);

                if (i < (max + 5) && i > (min - 5)) {
                    codes.add(line);
                }
                // read next line
                line = reader.readLine();
            }
            reader.close();
        } catch (IOException e) {
            e.printStackTrace();
        }

        return codes;
    }

    private static ArrayList<Integer> getSnippetCodeRange(String pathFile, ArrayList<Integer> lines) {
        ArrayList<Integer> lineRange = new ArrayList<>();
        int min, max, length;
        length = lines.size();
        if (length == 1) {
            min = max = lines.get(0).intValue();
        } else {
            min = lines.get(0).intValue();
            max = lines.get(0).intValue();
            for (int i = 1; i < length; i++) {
                if ( lines.get(i).intValue() < min) {
                    min = lines.get(i).intValue();
                }
                if ( lines.get(i).intValue() > max) {
                    max = lines.get(i).intValue();
                }
            }
        }
        Integer numberOfLines = FileUtil.getLinesOfFile(pathFile);
        if (min-5 < 0) {
            min = 0;
        }
        if (max+5 > numberOfLines-1) {
            max = numberOfLines - 1;
        }
        lineRange.add(min);
        lineRange.add(max);
        return lineRange;
    }
    private static ResolvedFile resolveFile(int fileId, ArrayList<Query> queries, Path path) {
        String pathFile = path.toString();

        File file = new File(pathFile);
        String pathFileName = FileUtil.getFileName(pathFile);
        ArrayList<String> snippetCodes = new ArrayList<String>();
        ArrayList<Integer> lines = new ArrayList<Integer>();
        Integer linesSizeOfInputFile = FileUtil.getLinesOfFile(pathFile);
        ResolvedFile resolvedFile = new ResolvedFile(queries, "", "", lines, snippetCodes);
        try {
            List<String> addedJars = getNeededJars(file);
            for (String jar: addedJars){
                System.out.println(jar);
            }
            for (int i = 0; i < addedJars.size(); i++) {
                try {
                    TypeSolver jarTypeSolver = JarTypeSolver.getJarTypeSolver(addedJars.get(i));
                    synchronizedTypeSolver.add(jarTypeSolver);
                } catch (Exception e) {
                    System.out.println("=== Package corrupt! ===");
                    System.out.println("Corrupted jars: " + addedJars.get(i));
                    System.out.println("Please download the latest jar manually from maven repository!");
                    System.out.println("File location: " + file.toString());
                }
            }
            StaticJavaParser.getConfiguration().setSymbolResolver(new JavaSymbolSolver(synchronizedTypeSolver.getTypeSolver()));
            CompilationUnit cu;
            cu = StaticJavaParser.parse(file);

            ArrayList<Boolean> isMethodMatch = new ArrayList<Boolean>();
            ArrayList<Boolean> isResolved = new ArrayList<Boolean>();
            ArrayList<Boolean> isResolvedAndParameterMatch = new ArrayList<Boolean>();

            for (int i = 0; i < queries.size(); i++) {
                isMethodMatch.add(false);
                isResolved.add(false);
                isResolvedAndParameterMatch.add(false);
            }

            ArrayList<MethodCallExpr> methodCallExprs = (ArrayList<MethodCallExpr>) cu.findAll(MethodCallExpr.class);
            JSONArray methodDetails = new JSONArray();
            for (MethodCallExpr mce: methodCallExprs) {
                JSONObject methodDetail = new JSONObject();
                try {
                    Optional<BlockStmt> mceBlock = mce.findAncestor(BlockStmt.class);
                    String codeBlock = "";
                    Integer codeBlockLine = 0;
                    if (mceBlock.isPresent()) {
                        codeBlock = mceBlock.get().toString();
                    }
                    methodDetail.put("codeBlock", codeBlock);
                    methodDetail.put("codeBlockLine", mceBlock.get().getBegin().get().line);
                    ResolvedMethodDeclaration resolvedMethodDeclaration = mce.resolve();
                    String fullyQualifiedName = resolvedMethodDeclaration.getQualifiedName();
                    int mce_index = methodCallExprs.indexOf(mce);
                    JSONArray args = new JSONArray();
                    for (Expression arg: mce.getArguments()) {
                        args.put(arg.toString());
                    }
                    methodDetail.put("expression", mce.toString());
                    methodDetail.put("fqn", fullyQualifiedName);
                    if (mce.getScope().isPresent()) {
                        methodDetail.put("caller", mce.getScope().get().toString());
                    }
                    methodDetail.put("args", args);
                    Integer lineOfMethodCall = mce.getBegin().get().line;

                    methodDetail.put("file", pathFileName);
                    methodDetail.put("line", lineOfMethodCall);
                    methodDetails.put(methodDetail);

                } catch (UnsolvedSymbolException unsolvedSymbolException) {
//                    unsolvedSymbolException.printStackTrace();
                    if (mce.getScope().isPresent()) {
                        Expression caller = mce.getScope().get();
                        try {
                            if (mce.getBegin().get().line == 255) {
                                int b = 1;
                            }
                            ResolvedType callerType = caller.calculateResolvedType();
                            methodDetails.put(methodDetail);
                        } catch (UnsolvedSymbolException unsolvedSymbolException1) {
//                            unsolvedSymbolException1.printStackTrace();
                        }
                    }
                } catch (Exception e) {}
            }
            try {
                System.out.println("methodDetails.size: " + methodDetails.toList().size());
                String pathMethodJSON = pathFile.replace(CODE_DIR, METHOD_OUT_DIR);
                pathMethodJSON = pathMethodJSON.replace(".txt", ".json");
                System.out.println(pathMethodJSON);
                File jsonFile = new File(pathMethodJSON);
                FileWriter jsonFileWriter = new FileWriter(jsonFile, false);
                methodDetails.write(jsonFileWriter, 2, 0);
                jsonFileWriter.close();
            }
            catch (IOException ioException) {
                System.out.println("IOException for file with input " + pathFile);
            }

            boolean isSuccess = true;
            if (queries.size() == 0) {
                isSuccess = false;
                System.out.println("No queries (API to be checked) available. Please check");
            }
            for (int i = 0; i < queries.size(); i++) {
                System.out.println("Query " + (i + 1) + ": " + queries.get(i));
                if (isMethodMatch.get(i)) {
                    if (isResolved.get(i)) {
                        System.out.println("Resolved");
                    }
                }
            }

            if (isSuccess) {
                resolvedFile.setPathFile(pathFile);
                resolvedFile.setLines(lines);
//                resolvedFile.setCodes(getSnippetCode(pathFile, lines));
                System.out.println("=== SUCCESS ===");
            } else {
                System.out.println("File location: " + file.toString());
            }

        } catch (ParseProblemException parseProblemException) {
            System.out.println("=== Parse Problem Exception in Type Resolution ===");
            System.out.println("File location: " + pathFile);
        } catch (RuntimeException runtimeException) {
            System.out.println("=== Runtime Exception in Type Resolution ===");
            System.out.println("File location: " + pathFile);
            runtimeException.printStackTrace();
        } catch (IOException io) {
            System.out.println("=== IO Exception in Type Resolution ===");
            System.out.println("File location: " + pathFile);
        }

        return resolvedFile;
    }
    public static void main(String[] args) {
        // Read code files from CODE_DIR
        ArrayList<Path> filePaths = null;
        QueriesUtil qUtil = new QueriesUtil();
        try {
            filePaths = getFilesFromFolder(CODE_DIR);
        }
        catch(IOException e) {
            e.printStackTrace();
        }
        for (Path path: filePaths) {
            // Resolve Type for each file
            System.out.println(path);
            int index = filePaths.indexOf(path);
            // queries do not play any roles in this modified tool, however it is not deleted to avoid modifying other classes and methods
            ArrayList<Query> queries = qUtil.parseQueries("com.alibaba.fastjson.util.ServiceLoader#load(java.lang.Class, java.lang.ClassLoader)");
            ResolvedFile resolvedFile = resolveFile(index, queries, path);
        }

    }
}
