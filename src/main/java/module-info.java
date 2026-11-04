module ni.edu.uam.proyectofinalivsemestre {
    requires javafx.controls;
    requires javafx.fxml;
    requires java.sql;

    opens ni.edu.uam.proyectofinalivsemestre to javafx.fxml;
    opens ni.edu.uam.proyectofinalivsemestre.model to javafx.base;

    exports ni.edu.uam.proyectofinalivsemestre;
    exports ni.edu.uam.proyectofinalivsemestre.model;
    exports ni.edu.uam.proyectofinalivsemestre.dao;
}
