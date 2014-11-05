package pt.inesc.voldemortClient;

import java.util.Scanner;

import pt.inesc.ask.proto.AskProto;
import pt.inesc.ask.proto.AskProto.Answer;
import pt.inesc.ask.proto.AskProto.Comment;
import pt.inesc.ask.proto.AskProto.Question;
import pt.inesc.proxy.ProxyWorker;
import voldemort.undoTracker.SRD;
import voldemort.versioning.Versioned;

public class DebugVoldemortClient {

    VoldemortStore<String, AskProto.Question> questions;
    VoldemortStore<String, AskProto.Answer> answers;
    VoldemortStore<String, AskProto.Comment> comments;
    short branch = 0;
    boolean restrain = false;

    public DebugVoldemortClient(String bootstrapUrl) {
        questions = new VoldemortStore<String, AskProto.Question>("questionStore", bootstrapUrl);
        answers = new VoldemortStore<String, AskProto.Answer>("answerStore", bootstrapUrl);
        comments = new VoldemortStore<String, AskProto.Comment>("commentStore", bootstrapUrl);
    }

    public static void main(String[] args) {
        String bootstrapUrl = "tcp://database:6666";
        if (args.length == 1) {
            bootstrapUrl = args[1];
        }
        // TODO set branch
        DebugVoldemortClient client = new DebugVoldemortClient(bootstrapUrl);
        Scanner s = new Scanner(System.in);
        System.out.println("Enter the key to get: ");
        String key = s.next();
        while (key != "exit") {
            System.out.println(client.get(key));
            System.out.println("Enter the key to get: ");
            key = s.next();
        }
    }


    public String get(String key) {
        StringBuilder sb = new StringBuilder();
        Versioned<Question> q = questions.get(key, new SRD(ProxyWorker.getTimestamp(), branch, restrain));
        if (q != null) {
            Question question = q.getValue();
            sb.append(question);
            sb.append(question.getTitle());
            sb.append("\n");
            sb.append(question.getAnswerIdsList());
            return sb.toString();
        }
        System.out.println("Not found in QuestionStore");


        Versioned<AskProto.Answer> a = answers.get(key, new SRD(ProxyWorker.getTimestamp(), branch, restrain));
        if (a != null) {
            Answer answer = a.getValue();
            sb.append(answer);
            sb.append(answer.getText());
            sb.append("\n");
            sb.append(answer.getCommentIdsList());
            return sb.toString();
        }

        System.out.println("Not found in AnswerStore");

        Versioned<AskProto.Comment> c = comments.get(key, new SRD(ProxyWorker.getTimestamp(), branch, restrain));
        if (c != null) {
            Comment comment = c.getValue();
            sb.append(comment);
            sb.append(comment.getText());
            sb.append("\n");
            return sb.toString();
        }
        sb.append("NOT FOUND ANYWHERE");
        return sb.toString();
    }
}
