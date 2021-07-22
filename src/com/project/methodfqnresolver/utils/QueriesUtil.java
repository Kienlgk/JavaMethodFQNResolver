package com.project.methodfqnresolver.utils;

import com.project.methodfqnresolver.model.Query;

import java.util.ArrayList;

public class QueriesUtil {

    public static void printQuery(ArrayList<Query> queries) {
        System.out.println("============");
        System.out.println("Your Queries");
        System.out.println("============");
        for (int i = 0; i < queries.size(); i++) {
            System.out.println("Query " + (i + 1) + ": " + queries.get(i));
        }
    }

    public static String prepareQuery(ArrayList<Query> queries) {
        String queriesAsString = "";
        for (int i = 0; i < queries.size(); i++) {
            queriesAsString += queries.get(i).toStringRequest();
            if (i != (queries.size() - 1)) queriesAsString += " ";
        }

        return queriesAsString;
    }

    public static ArrayList<Query> parseQueries(String s) {
        ArrayList<Query> queries = new ArrayList<Query>();

        s = s.replace(" ", "");
        while (!s.equals("")) {
            int tagLocation = s.indexOf('#');
            int leftBracketLocation = s.indexOf('(');
            int rightBracketLocation = s.indexOf(')');
            if (tagLocation == -1 | leftBracketLocation == -1 || rightBracketLocation == -1
                    && tagLocation < leftBracketLocation && leftBracketLocation < rightBracketLocation) {
                System.out.println("Your query isn't accepted");
                System.out.println("Query Format: " + "method(argument_1, argument_2, ... , argument_n)");
                System.out.println("Example: "
                        + "android.app.Notification.Builder#addAction(int, java.lang.CharSequence, android.app.PendingIntent)");
                ;
                return new ArrayList<Query>();
            } else {
                String fullyQualifiedName = s.substring(0, tagLocation);
                String method = s.substring(tagLocation + 1, leftBracketLocation);
                String args = s.substring(leftBracketLocation + 1, rightBracketLocation);
                ArrayList<String> arguments = new ArrayList<String>();
                if (!args.equals("")) { // handle if no arguments
                    String[] arr = args.split(",");
                    for (int i = 0; i < arr.length; i++) {
                        arguments.add(arr[i]);
                    }
                }
                Query query = new Query();
                query.setFullyQualifiedName(fullyQualifiedName);
                query.setMethod(method);
                query.setArguments(arguments);
                queries.add(query);
                int andLocation = s.indexOf('&');
                if (andLocation == -1) {
                    s = "";
                } else {
                    s = s.substring(andLocation + 1);
                }
            }
        }

        if (prepareQuery(queries).length() > 128) {
            System.out.println("I'm sorry");
            System.out.println("Your query length can't more than 128");
            System.out.println("Your query length are: " + prepareQuery(queries).length());
            System.out.println("This is github search rule, I can't do anything to tackle this problem");
            return new ArrayList<Query>();
        }

        return queries;
    }

}
