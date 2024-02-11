package updatetool.common;

public class NewExtraData extends ExtraData {

    protected NewExtraData(String data) {
        if(data != null) {
            if(data.length() > 2) {
                String toParse = data.substring(2, data.length()-2);
                String[] parsed = toParse.split("\",\"");
                for(String str : parsed) {
                    String[] parsed2 = str.split("\":\"");
                    mapping.put(parsed2[0], parsed2.length > 1 ? parsed2[1] : "");
                }
            }
        }
    }
    
    @Override
    public String export() {
        if(mapping.isEmpty())
            return null;
        StringBuilder sb = new StringBuilder(2000);
        sb.append("{");
        for(var entry: mapping.entrySet()) {
            sb.append('"');
            sb.append(entry.getKey());
            sb.append("\":\"");
            sb.append(entry.getValue());
            sb.append('"');
            sb.append(",");
        }
        sb.deleteCharAt(sb.length()-1);
        sb.append("}");
        return sb.toString();
    }
}
