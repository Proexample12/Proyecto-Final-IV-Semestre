module ni.edu.uam.proyectofinalivsemestre {
    requires javafx.controls;
    requires javafx.fxml;


    opens ni.edu.uam.proyectofinalivsemestre to javafx.fxml;
    exports ni.edu.uam.proyectofinalivsemestre;
}