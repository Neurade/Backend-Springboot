package app.demo.neurade.infrastructures.repositories;

import app.demo.neurade.domain.models.AIPackageInstance;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AIPackageInstanceRepository extends JpaRepository<AIPackageInstance, UUID> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select a from AIPackageInstance a where a.id = :id")
    Optional<AIPackageInstance> findByIdForUpdate(@Param("id") UUID id);

    @Modifying
    @Query("""
        delete from AIPackageInstance a
        where a.classRoom.id = :classId
    """)
    int deleteClassroomInstance(@Param("classId") Long classId);

    @Modifying
    @Query("""
        delete from AIPackageInstance a
        where a.buyer.id = :buyerId
          and a.classRoom is null
    """)
    int deletePersonalInstance(@Param("buyerId") Long buyerId);

    @Query("""
        SELECT a FROM AIPackageInstance a
        WHERE a.buyer.id = :buyerId
        AND a.classRoom IS NULL
        """)
    List<AIPackageInstance> findPersonalInstance(Long buyerId);
    Optional<AIPackageInstance> findByClassRoom_Id(Long classRoomId);
}
